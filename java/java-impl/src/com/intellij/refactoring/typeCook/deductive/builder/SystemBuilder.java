/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.typeCook.deductive.builder;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeCook.Settings;
import com.intellij.refactoring.typeCook.Util;
import com.intellij.refactoring.typeCook.deductive.PsiTypeVariableFactory;
import com.intellij.refactoring.typeCook.deductive.util.VictimCollector;
import com.intellij.util.containers.HashMap;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SystemBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.typeCook.deductive.builder.SystemBuilder");

  private final PsiManager myManager;
  private final Map<PsiElement, Boolean> myMethodCache;
  private final Map<PsiParameter, PsiParameter> myParameters;
  private final Map<PsiMethod, PsiMethod> myMethods;
  private final Map<PsiElement, PsiType> myTypes;
  private final Set<PsiAnchor> myVisitedConstructions;
  private final Settings mySettings;
  private final PsiTypeVariableFactory myTypeVariableFactory;
  private final Project myProject;

  public SystemBuilder(final Project project, final Settings settings) {
    myProject = project;
    myManager = PsiManager.getInstance(myProject);
    mySettings = settings;
    myMethodCache = new HashMap<>();
    myParameters = new HashMap<>();
    myMethods = new HashMap<>();
    myTypes = new HashMap<>();
    myVisitedConstructions = new HashSet<>();
    myTypeVariableFactory = new PsiTypeVariableFactory();
  }

  private Set<PsiElement> collect(final PsiElement[] scopes) {
    return new VictimCollector(scopes, mySettings).getVictims();
  }

  private boolean verifyMethod(final PsiElement element, final Set<PsiElement> victims, final PsiSearchHelper helper) {
    PsiMethod method;
    PsiParameter parameter = null;
    int index = 0;

    if (element instanceof PsiMethod) {
      method = (PsiMethod)element;
    }
    else if (element instanceof PsiParameter) {
      parameter = (PsiParameter)element;
      method = (PsiMethod)parameter.getDeclarationScope();
      index = method.getParameterList().getParameterIndex(parameter);
    }
    else {
      LOG.error("Parameter or method expected, but found " + (element == null ? "null" : element.getClass().getName()));
      return false;
    }

    final PsiMethod superMethod = method.findDeepestSuperMethod();
    PsiMethod keyMethod;
    PsiParameter keyParameter = null;

    if (superMethod != null) {
      Boolean good = myMethodCache.get(superMethod);

      if (good != null && !good.booleanValue()) {
        return false;
      }

      final PsiElement e = parameter == null ? superMethod : superMethod.getParameterList().getParameters()[index];

      if (!victims.contains(e)) {
        myMethodCache.put(superMethod, Boolean.FALSE);
        return false;
      }

      keyMethod = superMethod;

      myMethods.put(method, keyMethod);

      if (parameter != null) {
        keyParameter = (PsiParameter)e;
      }
    }
    else {
      Boolean good = myMethodCache.get(method);

      if (good != null && good.booleanValue()) {
        myMethods.putIfAbsent(method, method);

        if (parameter != null && myParameters.get(parameter) == null) {
          myParameters.put(parameter, parameter);
        }

        return true;
      }

      keyMethod = method;
      keyParameter = parameter;
    }

    final PsiMethod[] overriders = OverridingMethodsSearch.search(keyMethod).toArray(PsiMethod.EMPTY_ARRAY);

    for (final PsiMethod overrider : overriders) {
      final PsiElement e = parameter != null ? overrider.getParameterList().getParameters()[index] : overrider;

      if (!victims.contains(e)) {
        myMethodCache.put(keyMethod, Boolean.FALSE);
        return false;
      }
    }

    for (final PsiMethod overrider : overriders) {
      final PsiElement e = parameter != null ? overrider.getParameterList().getParameters()[index] : overrider;

      myMethods.put(overrider, keyMethod);

      if (parameter != null) {
        myParameters.put((PsiParameter)e, keyParameter);
      }
    }

    myMethods.put(method, keyMethod);

    if (parameter != null) {
      myParameters.put(parameter, keyParameter);
    }

    myMethodCache.put(keyMethod, Boolean.TRUE);

    return true;
  }

  private void setType(final PsiElement e, final PsiType t) {
    myTypes.put(e, t);
  }

  private PsiType defineType(final PsiElement e) {
    PsiType t = myTypes.get(e);

    if (t != null) {
      return t;
    }

    t = Util.getType(e);

    final PsiType parameterizedType = Util.createParameterizedType(t, myTypeVariableFactory, e);

    myTypes.put(e, parameterizedType);

    return parameterizedType;
  }

  private PsiType getType(final PsiElement e) {
    final PsiType t = myTypes.get(e);

    if (t != null) {
      return t;
    }

    return Util.banalize(Util.getType(e));
  }

  private boolean isCooked(final PsiElement element) {
    return myTypes.get(element) != null;
  }

  public PsiType inferTypeForMethodTypeParameter(final PsiTypeParameter typeParameter,
                                                 final PsiParameter[] parameters,
                                                 PsiExpression[] arguments,
                                                 PsiSubstitutor partialSubstitutor,
                                                 PsiElement parent,
                                                 ReductionSystem system) {
    PsiType substitution = PsiType.NULL;
    PsiResolveHelper helper = JavaPsiFacade.getInstance(typeParameter.getProject()).getResolveHelper();
    if (parameters.length > 0) {
      for (int j = 0; j < arguments.length; j++) {
        PsiExpression argument = arguments[j];
        final PsiParameter parameter = parameters[Math.min(j, parameters.length - 1)];
        if (j >= parameters.length && !parameter.isVarArgs()) break;
        PsiType parameterType = parameter.getType();
        PsiType argumentType = evaluateType(argument, system);

        if (parameterType instanceof PsiEllipsisType) {
          parameterType = ((PsiEllipsisType)parameterType).getComponentType();
          if (arguments.length == parameters.length &&
              argumentType instanceof PsiArrayType &&
              !(((PsiArrayType)argumentType).getComponentType() instanceof PsiPrimitiveType)) {
            argumentType = ((PsiArrayType)argumentType).getComponentType();
          }
        }
        final PsiType currentSubstitution =
          helper.getSubstitutionForTypeParameter(typeParameter, parameterType, argumentType, true, PsiUtil.getLanguageLevel(parent));
        if (currentSubstitution == null) {
          substitution = null;
          break;
        }
        else if (currentSubstitution instanceof PsiWildcardType) {
          if (substitution instanceof PsiWildcardType) return PsiType.NULL;
        }
        else if (PsiType.NULL.equals(currentSubstitution)) continue;

        if (PsiType.NULL.equals(substitution)) {
          substitution = currentSubstitution;
          continue;
        }
        if (!substitution.equals(currentSubstitution)) {
          if (substitution instanceof PsiTypeVariable || currentSubstitution instanceof PsiTypeVariable) {
            substitution = GenericsUtil.getLeastUpperBound(substitution, currentSubstitution, typeParameter.getManager());
            if (substitution == null) break;
          }
        }
      }
    }

    if (PsiType.NULL.equals(substitution)) {
      substitution = inferMethodTypeParameterFromParent(typeParameter, partialSubstitutor, parent, system);
    }
    return substitution;
  }

  private PsiType inferMethodTypeParameterFromParent(final PsiTypeParameter typeParameter,
                                                     PsiSubstitutor substitutor,
                                                     PsiElement parent,
                                                     ReductionSystem system) {
    PsiTypeParameterListOwner owner = typeParameter.getOwner();
    PsiType substitution = PsiType.NULL;
    if (owner instanceof PsiMethod) {
      if (parent instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression methodCall = (PsiMethodCallExpression)parent;
        substitution = inferMethodTypeParameterFromParent(methodCall.getParent(), methodCall, typeParameter, substitutor, system);
      }
    }
    return substitution;
  }

  private PsiType inferMethodTypeParameterFromParent(PsiElement parent,
                                                     PsiMethodCallExpression methodCall,
                                                     final PsiTypeParameter typeParameter,
                                                     PsiSubstitutor substitutor,
                                                     ReductionSystem system) {
    PsiType type = null;

    if (parent instanceof PsiVariable && methodCall.equals(((PsiVariable)parent).getInitializer())) {
      type = getType(parent);
    }
    else if (parent instanceof PsiAssignmentExpression && methodCall.equals(((PsiAssignmentExpression)parent).getRExpression())) {
      type = evaluateType(((PsiAssignmentExpression)parent).getLExpression(), system);
    }
    else if (parent instanceof PsiTypeCastExpression && methodCall.equals(((PsiTypeCastExpression)parent).getOperand())) {
      type = evaluateType((PsiExpression)parent, system);
    }
    else if (parent instanceof PsiReturnStatement) {
      PsiMethod method = PsiTreeUtil.getParentOfType(parent, PsiMethod.class);
      if (method != null) {
        type = getType(method);
      }
    }

    if (type == null) {
      type = PsiType.getJavaLangObject(methodCall.getManager(), methodCall.getResolveScope());
    }

    PsiType returnType = ((PsiMethod)typeParameter.getOwner()).getReturnType();
    PsiType guess = JavaPsiFacade.getInstance(parent.getProject()).getResolveHelper()
      .getSubstitutionForTypeParameter(typeParameter, returnType, type, false, PsiUtil.getLanguageLevel(parent));

    if (PsiType.NULL.equals(guess)) {
      PsiType superType = substitutor.substitute(typeParameter.getSuperTypes()[0]);
      return superType == null ? PsiType.getJavaLangObject(methodCall.getManager(), methodCall.getResolveScope()) : superType;
    }

    //The following code is the result of deep thought, do not shit it out before discussing with [ven]
    if (returnType instanceof PsiClassType && typeParameter.equals(((PsiClassType)returnType).resolve())) {
      PsiClassType[] extendsTypes = typeParameter.getExtendsListTypes();
      PsiSubstitutor newSubstitutor = substitutor.put(typeParameter, guess);
      for (PsiClassType t : extendsTypes) {
        PsiType extendsType = newSubstitutor.substitute(t);
        if (!extendsType.isAssignableFrom(guess)) {
          if (guess.isAssignableFrom(extendsType)) {
            guess = extendsType;
            newSubstitutor = substitutor.put(typeParameter, guess);
          }
          else {
            break;
          }
        }
      }
    }

    return guess;
  }

  PsiType evaluateType(final PsiExpression expr, final ReductionSystem system) {
    if (expr instanceof PsiArrayAccessExpression && !mySettings.preserveRawArrays()) {
      final PsiType at = evaluateType(((PsiArrayAccessExpression)expr).getArrayExpression(), system);

      if (at instanceof PsiArrayType) {
        return ((PsiArrayType)at).getComponentType();
      }
    }
    else if (expr instanceof PsiAssignmentExpression) {
      return evaluateType(((PsiAssignmentExpression)expr).getLExpression(), system);
    }
    else if (expr instanceof PsiCallExpression) {
      final PsiCallExpression call = (PsiCallExpression)expr;
      final PsiMethod method = call.resolveMethod();

      if (method != null) {
        final PsiClass aClass = method.getContainingClass();
        final PsiTypeParameter[] methodTypeParameters = method.getTypeParameters();
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        final PsiExpression[] arguments = call.getArgumentList().getExpressions();
        final PsiExpression qualifier =
          expr instanceof PsiMethodCallExpression ? ((PsiMethodCallExpression)expr).getMethodExpression().getQualifierExpression() : null;

        final Set<PsiTypeParameter> typeParameters = new HashSet<>(Arrays.asList(methodTypeParameters));

        PsiSubstitutor qualifierSubstitutor = PsiSubstitutor.EMPTY;
        PsiSubstitutor supertypeSubstitutor = PsiSubstitutor.EMPTY;

        PsiType aType;

        if (method.isConstructor()) {
          if (expr instanceof PsiNewExpression) {
            aType = isCooked(expr) ? getType(expr) : expr.getType();
            qualifierSubstitutor = Util.resolveType(aType).getSubstitutor();
          }
          else {
            LOG.assertTrue(expr instanceof PsiMethodCallExpression); //either this(); or super();
            final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)expr).getMethodExpression();
            if (PsiKeyword.THIS.equals(methodExpression.getText())) {
              aType = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory().createType(aClass);
            }
            else {
              LOG.assertTrue(PsiKeyword.SUPER.equals(methodExpression.getText()));
              PsiClass placeClass = PsiTreeUtil.getParentOfType(expr, PsiClass.class);
              qualifierSubstitutor = TypeConversionUtil.getClassSubstitutor(aClass, placeClass, PsiSubstitutor.EMPTY);
              aType = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory().createType(aClass, qualifierSubstitutor);
            }
          }
        }
        else {
          aType = getType(method);
        }

        if (qualifier != null) {
          final PsiType qualifierType = evaluateType(qualifier, system);
          final PsiClassType.ClassResolveResult result = Util.resolveType(qualifierType);

          if (result.getElement() != null) {
            final PsiClass qualifierClass = result.getElement();

            qualifierSubstitutor = TypeConversionUtil.getClassSubstitutor(aClass, qualifierClass, result.getSubstitutor());

            if (qualifierSubstitutor != null) {
              aType = qualifierSubstitutor.substitute(aType);
            }
          }
        }

        final Map<PsiTypeParameter, PsiType> mapping = new HashMap<>();

        for (int i = 0; i < Math.min(parameters.length, arguments.length); i++) {
          final PsiType argumentType = evaluateType(arguments[i], system);

          PsiType parmType;

          if (isCooked(parameters[i])) {
            parmType = getType(parameters[i]);
            system.addSubtypeConstraint(argumentType, parmType);
          }
          else {
            parmType = parameters[i].getType();
            if (qualifierSubstitutor != null) {
              parmType = qualifierSubstitutor.substitute(parmType);
            }

            if (!Util.bindsTypeVariables(parmType) && !Util.bindsTypeParameters(parmType, typeParameters)) {
              parmType = Util.banalize(parmType);
            }

            final PsiType theType = new Object() {
              PsiType introduceAdditionalTypeVariables(final PsiType type, final PsiSubstitutor qualifier, final PsiSubstitutor supertype) {
                final int level = type.getArrayDimensions();
                final PsiClassType.ClassResolveResult result = Util.resolveType(type);
                final PsiClass aClass = result.getElement();

                if (aClass != null) {
                  if (aClass instanceof PsiTypeParameter) {
                    final PsiTypeParameter tp = (PsiTypeParameter)aClass;
                    final PsiClassType[] extypes = tp.getExtendsListTypes();

                    PsiType pv = mapping.get(tp);

                    if (pv == null) {
                      pv = myTypeVariableFactory.create();
                      mapping.put(tp, pv);
                    }

                    for (final PsiClassType ext : extypes) {
                      final PsiType extype = qualifier.substitute(new Object() {
                        public PsiType substitute(final PsiType ext) {
                          final PsiClassType.ClassResolveResult result = Util.resolveType(ext);
                          final PsiClass aClass = result.getElement();

                          if (aClass != null) {
                            if (aClass instanceof PsiTypeParameter) {
                              final PsiType type = mapping.get(aClass);

                              if (type != null) {
                                return type;
                              }

                              return ext;
                            }

                            final PsiSubstitutor aSubst = result.getSubstitutor();
                            PsiSubstitutor theSubst = PsiSubstitutor.EMPTY;

                            for (final PsiTypeParameter parm : aSubst.getSubstitutionMap().keySet()) {
                              PsiType type = aSubst.substitute(parm);

                              if (type != null) {
                                if (type instanceof PsiWildcardType) {
                                  final PsiWildcardType wildcard = (PsiWildcardType)type;
                                  final PsiType bound = wildcard.getBound();
                                  if (bound != null) {
                                    final PsiManager manager = parm.getManager();
                                    type = wildcard.isExtends()
                                           ? PsiWildcardType.createExtends(manager, substitute(bound))
                                           : PsiWildcardType.createSuper(manager, substitute(bound));
                                  }
                                }
                                else {
                                  type = substitute(type);
                                }
                              }

                              theSubst = theSubst.put(parm, type);
                            }

                            return JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory()
                              .createType(aClass, theSubst);
                          }

                          return ext;
                        }
                      }.substitute(ext));
                      system.addSubtypeConstraint(pv, extype);
                    }

                    return Util.createArrayType(pv, level);
                  }

                  final Map<PsiTypeParameter, PsiType> substitutionMap = result.getSubstitutor().getSubstitutionMap();

                  PsiSubstitutor theSubst = PsiSubstitutor.EMPTY;

                  for (final PsiTypeParameter p : substitutionMap.keySet()) {
                    final PsiType pType = substitutionMap.get(p);

                    if (pType instanceof PsiWildcardType) {
                      final PsiWildcardType wildcard = (PsiWildcardType)pType;
                      final PsiType theBound = wildcard.getBound();

                      if (theBound != null) {
                        final PsiType bound = qualifier.substitute(supertype.substitute(theBound));

                        if (Util.bindsTypeVariables(bound)) {
                          final PsiType var = myTypeVariableFactory.create();

                          if (wildcard.isExtends()) {
                            system.addSubtypeConstraint(var, bound);
                          }
                          else {
                            system.addSubtypeConstraint(bound, var);
                          }

                          theSubst = theSubst.put(p, var);
                        }
                        else if (Util.bindsTypeParameters(bound, typeParameters)) {
                          final PsiType var = myTypeVariableFactory.create();
                          PsiSubstitutor subst = PsiSubstitutor.EMPTY;

                          for (final PsiTypeParameter aTypeParm : methodTypeParameters) {
                            PsiType parmVar = mapping.get(aTypeParm);

                            if (parmVar == null) {
                              parmVar = myTypeVariableFactory.create();
                              mapping.put(aTypeParm, parmVar);
                            }

                            subst = subst.put(aTypeParm, parmVar);
                          }

                          final PsiType bnd = subst.substitute(bound);

                          if (wildcard.isExtends()) {
                            system.addSubtypeConstraint(bnd, var);
                          }
                          else {
                            system.addSubtypeConstraint(var, bnd);
                          }

                          theSubst = theSubst.put(p, var);
                        }
                        else {
                          theSubst = theSubst.put(p, pType);
                        }
                      }
                      else {
                        theSubst = theSubst.put(p, pType);
                      }
                    }
                    else if (pType != null) {
                      theSubst = theSubst.put(p, introduceAdditionalTypeVariables(pType, qualifier, supertype));
                    }
                  }

                  return Util.createArrayType(JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(aClass, theSubst), level);
                }

                return Util.createArrayType(type, level);
              }
            }.introduceAdditionalTypeVariables(parmType, qualifierSubstitutor, supertypeSubstitutor);

            system.addSubtypeConstraint(argumentType, theType);
          }
        }

        PsiSubstitutor theSubst = PsiSubstitutor.EMPTY;

        for (final PsiTypeParameter parm : mapping.keySet()) {
          final PsiType type = mapping.get(parm);

          theSubst = theSubst.put(parm, type);
        }

        for (PsiTypeParameter typeParam : methodTypeParameters) {
          final PsiType inferred = inferTypeForMethodTypeParameter(typeParam, parameters, arguments, theSubst, expr, system);
          theSubst = theSubst.put(typeParam, inferred);
        }

        return theSubst.substitute(aType);
      }
    }
    else if (expr instanceof PsiParenthesizedExpression) {
      return evaluateType(((PsiParenthesizedExpression)expr).getExpression(), system);
    }
    else if (expr instanceof PsiConditionalExpression) {
      return evaluateType(((PsiConditionalExpression)expr).getThenExpression(), system);
    }
    else if (expr instanceof PsiReferenceExpression) {
      final PsiReferenceExpression ref = (PsiReferenceExpression)expr;
      final PsiExpression qualifier = ref.getQualifierExpression();

      if (qualifier == null) {
        return getType(ref.resolve());
      }
      else {
        final PsiType qualifierType = evaluateType(qualifier, system);
        final PsiElement element = ref.resolve();

        final PsiClassType.ClassResolveResult result = Util.resolveType(qualifierType);

        if (result.getElement() != null) {
          final PsiClass aClass = result.getElement();
          final PsiSubstitutor aSubst = result.getSubstitutor();

          if (element instanceof PsiField) {
            final PsiField field = (PsiField)element;
            final PsiType fieldType = getType(field);
            final PsiClass superClass = field.getContainingClass();

            PsiType aType = fieldType;

            if (!aClass.equals(superClass) && field.isPhysical()) {
              aType = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY).substitute(aType);
            }

            return aSubst.substitute(aType);
          }
        }
        else if (element != null) {
          return getType(element);
        }
      }
    }

    return getType(expr);
  }


  private void addUsage(final ReductionSystem system, final PsiElement element) {

    if (element instanceof PsiVariable) {
      final PsiExpression initializer = ((PsiVariable)element).getInitializer();

      if (initializer != null) {
        final PsiExpression core = PsiUtil.deparenthesizeExpression(initializer);

        if (core instanceof PsiArrayInitializerExpression) {
          final PsiExpression[] inits = ((PsiArrayInitializerExpression)core).getInitializers();
          final PsiType type = getType(element);

          for (PsiExpression init : inits) {
            system.addSubtypeConstraint(evaluateType(init, system).createArrayType(), type);
          }
        }
        else if (core instanceof PsiNewExpression) {
          final PsiArrayInitializerExpression init = ((PsiNewExpression)core).getArrayInitializer();

          if (init != null) {
            final PsiExpression[] inits = init.getInitializers();
            final PsiType type = getType(element);

            for (PsiExpression init1 : inits) {
              system.addSubtypeConstraint(evaluateType(init1, system).createArrayType(), type);
            }
          }

          system.addSubtypeConstraint(evaluateType(core, system), getType(element));
        }
        else {
          system.addSubtypeConstraint(evaluateType(core, system), getType(element));
        }
      }

      if (element instanceof PsiParameter) {
        PsiParameter parameter = (PsiParameter)element;
        final PsiElement declarationScope = parameter.getDeclarationScope();
        if (declarationScope instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)declarationScope;
          final PsiSearchHelper helper = PsiSearchHelper.SERVICE.getInstance(myManager.getProject());
          SearchScope scope = getScope(helper, method);

          for (PsiReference ref : ReferencesSearch.search(method, scope, true)) {
            final PsiElement elt = ref.getElement();

            if (elt != null) {
              final PsiCallExpression call = PsiTreeUtil.getParentOfType(elt, PsiCallExpression.class);

              if (call != null) {
                PsiExpressionList argList = call.getArgumentList();
                if (argList != null) {
                  PsiExpression[] args = argList.getExpressions();
                  int index = method.getParameterList().getParameterIndex(parameter);
                  if (index < args.length) {
                    system.addSubtypeConstraint(evaluateType(args[index], system), myTypes.get(element));
                  }
                }
              }
            }
          }
        } else if (declarationScope instanceof PsiForeachStatement) {
          addForEachConstraint(system, (PsiForeachStatement)declarationScope);
        }
      }
      return;
    }
    else if (element instanceof PsiMethod) {
      final PsiType reType = getType(element);

      element.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override public void visitReturnStatement(final PsiReturnStatement statement) {
          super.visitReturnStatement(statement);

          final PsiExpression retExpr = statement.getReturnValue();

          if (retExpr != null) {
            system.addSubtypeConstraint(evaluateType(retExpr, system), reType);
          }
        }

        @Override
        public void visitClass(PsiClass aClass) {}

        @Override
        public void visitLambdaExpression(PsiLambdaExpression expression) {}
      });

      return;
    }

    final PsiElement root = PsiTreeUtil.getParentOfType(element, PsiStatement.class, PsiField.class);

    if (root != null) {
      final PsiAnchor anchor = PsiAnchor.create(root);

      if (!myVisitedConstructions.contains(anchor)) {
        root.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override public void visitAssignmentExpression(final PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);

            system
              .addSubtypeConstraint(evaluateType(expression.getRExpression(), system), evaluateType(expression.getLExpression(), system));
          }

          @Override public void visitConditionalExpression(final PsiConditionalExpression expression) {
            super.visitConditionalExpression(expression);

            system.addSubtypeConstraint(evaluateType(expression.getThenExpression(), system),
                                        evaluateType(expression.getElseExpression(), system));
            system.addSubtypeConstraint(evaluateType(expression.getElseExpression(), system),
                                        evaluateType(expression.getThenExpression(), system));
          }

          @Override public void visitCallExpression(final PsiCallExpression expression) {
            super.visitCallExpression(expression);
            evaluateType(expression, system);
          }

          @Override public void visitReturnStatement(final PsiReturnStatement statement) {
            super.visitReturnStatement(statement);

            final PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, true, PsiLambdaExpression.class);
            if (method != null) {
              system.addSubtypeConstraint(evaluateType(statement.getReturnValue(), system), getType(method));
            }
          }

          @Override public void visitTypeCastExpression(final PsiTypeCastExpression expression) {
            super.visitTypeCastExpression(expression);

            final PsiType operandType = evaluateType(expression.getOperand(), system);
            final PsiType castType = evaluateType(expression, system);
            if (operandType == null || castType == null) return;

            if (Util.bindsTypeVariables(operandType)) {
              system.addCast(expression, operandType);
            }

            if (operandType.getDeepComponentType() instanceof PsiTypeVariable ||
                castType.getDeepComponentType() instanceof PsiTypeVariable) {
              system.addSubtypeConstraint(operandType, castType);
            }
            else {
              final PsiClassType.ClassResolveResult operandResult = Util.resolveType(operandType);
              final PsiClassType.ClassResolveResult castResult = Util.resolveType(castType);

              final PsiClass operandClass = operandResult.getElement();
              final PsiClass castClass = castResult.getElement();

              if (operandClass != null && castClass != null) {
                if (InheritanceUtil.isInheritorOrSelf(operandClass, castClass, true)) {
                  system.addSubtypeConstraint(operandType, castType);
                }
              }
            }
          }

          @Override public void visitVariable(final PsiVariable variable) {
            super.visitVariable(variable);

            final PsiExpression init = variable.getInitializer();

            if (init != null) {
              system.addSubtypeConstraint(evaluateType(init, system), getType(variable));
            }
          }

          @Override public void visitNewExpression(final PsiNewExpression expression) {
            super.visitNewExpression(expression);

            final PsiArrayInitializerExpression init = expression.getArrayInitializer();

            if (init != null) {
              final PsiExpression[] inits = init.getInitializers();
              final PsiType type = getType(expression);

              for (PsiExpression init1 : inits) {
                system.addSubtypeConstraint(evaluateType(init1, system).createArrayType(), type);
              }
            }
          }

          @Override public void visitReferenceExpression(final PsiReferenceExpression expression) {
            final PsiExpression qualifierExpression = expression.getQualifierExpression();

            if (qualifierExpression != null) {
              qualifierExpression.accept(this);
            }
          }
        });

        myVisitedConstructions.add(anchor);
      }
    }
  }

  private static SearchScope getScope(final PsiSearchHelper helper, final PsiElement element) {
    SearchScope scope = helper.getUseScope(element);
    if (scope instanceof GlobalSearchScope) {
      scope =
        GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope)scope, StdFileTypes.JAVA, StdFileTypes.JSP, StdFileTypes.JSPX);
    }
    return scope;
  }

  PsiType replaceWildCards(final PsiType type, final ReductionSystem system, final PsiSubstitutor definedSubst) {
    if (type instanceof PsiWildcardType) {
      final PsiWildcardType wildcard = (PsiWildcardType)type;
      final PsiType var = myTypeVariableFactory.create();
      final PsiType bound = wildcard.getBound();

      if (bound != null) {
        if (wildcard.isExtends()) {
          system.addSubtypeConstraint(Util.banalize(definedSubst.substitute(replaceWildCards(bound, system, definedSubst))), var);
        }
        else {
          system.addSubtypeConstraint(var, Util.banalize(definedSubst.substitute(replaceWildCards(bound, system, definedSubst))));
        }
      }

      return var;
    }
    else if (type instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult result = Util.resolveType(type);
      final PsiClass aClass = result.getElement();
      final PsiSubstitutor aSubst = result.getSubstitutor();

      if (aClass != null) {
        PsiSubstitutor theSubst = PsiSubstitutor.EMPTY;

        for (final PsiTypeParameter p : aSubst.getSubstitutionMap().keySet()) {
          theSubst = theSubst.put(p, replaceWildCards(aSubst.substitute(p), system, definedSubst));
        }

        return JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(aClass, theSubst);
      }
    }

    return type;
  }

  private void addBoundConstraintsImpl(final PsiType defined, final PsiType type, final ReductionSystem system) {
    final PsiClassType.ClassResolveResult resultDefined = Util.resolveType(defined);
    final PsiClassType.ClassResolveResult resultType = Util.resolveType(type);
    final PsiClass definedClass = resultDefined.getElement();

    if (definedClass == null || !definedClass.equals(resultType.getElement())) {
      return;
    }

    final PsiSubstitutor definedSubst = resultDefined.getSubstitutor();
    final PsiSubstitutor typeSubst = resultType.getSubstitutor();

    for (final PsiTypeParameter parameter : definedSubst.getSubstitutionMap().keySet()) {
      final PsiClassType[] extendsList = parameter.getExtendsList().getReferencedTypes();
      final PsiType definedType = definedSubst.substitute(parameter);

      if (definedType instanceof PsiTypeVariable) {
        for (PsiType extendsType : extendsList) {
          extendsType = replaceWildCards(extendsType, system, definedSubst);

          system.addSubtypeConstraint(definedType, Util.banalize(definedSubst.substitute(extendsType)));
        }
      }
      else {
        addBoundConstraintsImpl(definedType, typeSubst.substitute(parameter), system);
      }
    }
  }


  private void addBoundConstraints(final ReductionSystem system, final PsiType definedType, final PsiElement element) {
    final PsiType elemenType = Util.getType(element);

    if (elemenType != null) {
      addBoundConstraintsImpl(definedType, elemenType, system);

      if (mySettings.cookObjects() && elemenType.getCanonicalText().equals(CommonClassNames.JAVA_LANG_OBJECT)) {
        system.addSubtypeConstraint(definedType, elemenType);
      }
    }
  }

  public ReductionSystem build(final PsiElement... scopes) {
    return build(collect(scopes));
  }

  public ReductionSystem build(final Set<PsiElement> victims) {
    final PsiSearchHelper helper = PsiSearchHelper.SERVICE.getInstance(myManager.getProject());

    ReductionSystem system = new ReductionSystem(myProject, victims, myTypes, myTypeVariableFactory, mySettings);

    for (final PsiElement element : victims) {
      if (element instanceof PsiParameter && ((PsiParameter)element).getDeclarationScope() instanceof PsiMethod ||
          element instanceof PsiMethod) {
        verifyMethod(element, victims, helper);
      }
    }

    for (final PsiElement element : victims) {
      PsiType definedType;
      if (element instanceof PsiParameter && ((PsiParameter)element).getDeclarationScope() instanceof PsiMethod) {
        final PsiParameter p = myParameters.get(element);

        if (p != null) {
          setType(element, definedType = defineType(p));
        }
        else {
          continue;
        }
      }
      else if (element instanceof PsiMethod) {
        final PsiMethod m = myMethods.get(element);

        if (m != null) {
          system.addSubtypeConstraint(defineType(element), definedType = defineType(m));
        }
        else {
          continue;
        }
      }
      else {
        definedType = defineType(element);
      }

      addBoundConstraints(system, definedType, element);
    }

    for (final PsiElement element : victims) {
      if (element instanceof PsiParameter) {
        final PsiElement scope = ((PsiParameter)element).getDeclarationScope();
        if (scope instanceof PsiMethod) {
          final PsiParameter p = myParameters.get(element);

          if (p == null) continue;
        }
        /*else if (scope instanceof PsiForeachStatement) {
          addForEachConstraint(system, (PsiForeachStatement)scope);
        }*/
        else if (element instanceof PsiMethod) {
          final PsiMethod m = myMethods.get(element);

          if (m == null) continue;
        }
      }
      else if (element instanceof PsiMethod) {
        final PsiMethod m = myMethods.get(element);

        if (m == null) continue;
      }

      addUsage(system, element);

      if (!(element instanceof PsiExpression)) {

        for (PsiReference ref : ReferencesSearch.search(element, getScope(helper, element), true)) {
          final PsiElement elt = ref.getElement();

          if (elt != null) {
            addUsage(system, elt);
          }
        }
      }
    }

    return system;
  }

  private void addForEachConstraint(final ReductionSystem system, final PsiForeachStatement statement) {
    final PsiType paramType = getType(statement.getIterationParameter());
    final PsiExpression value = statement.getIteratedValue();
    if (value != null) {
      final PsiType type = evaluateType(value, system);
      if (type instanceof PsiClassType) {
        final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
        final PsiClass clazz = resolveResult.getElement();
        if (clazz != null) {
          final PsiClass iterableClass =
            JavaPsiFacade.getInstance(clazz.getProject()).findClass("java.lang.Iterable", clazz.getResolveScope());
          if (iterableClass != null) {
            final PsiTypeParameter[] typeParameters = iterableClass.getTypeParameters();
            if (typeParameters.length == 1) {
              final PsiSubstitutor substitutor =
                TypeConversionUtil.getClassSubstitutor(iterableClass, clazz, resolveResult.getSubstitutor());
              if (substitutor != null) {
                final PsiType componentType = substitutor.substitute(typeParameters[0]);
                system.addSubtypeConstraint(componentType, paramType);
              }
            }
          }
        }
      }
      else if (type instanceof PsiArrayType) {
        system.addSubtypeConstraint(((PsiArrayType)type).getComponentType(), paramType);
      }
    }
  }
}
