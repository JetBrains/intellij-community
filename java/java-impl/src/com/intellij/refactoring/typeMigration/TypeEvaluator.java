/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.refactoring.typeMigration;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.intellij.util.Function;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;

/**
 * @author db
 * Date: 27.06.2003
 */
public class TypeEvaluator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.typeMigration.TypeEvaluator");

  private final HashMap<TypeMigrationUsageInfo, LinkedList<PsiType>> myTypeMap;
  private final TypeMigrationRules myRules;
  private final TypeMigrationLabeler myLabeler;

  public TypeEvaluator(final LinkedList<Pair<TypeMigrationUsageInfo, PsiType>> types, final TypeMigrationLabeler labeler) {
    myLabeler = labeler;
    myRules = labeler == null ? new TypeMigrationRules() : labeler.getRules();
    myTypeMap = new HashMap<>();

    if (types != null) {
      for (final Pair<TypeMigrationUsageInfo, PsiType> p : types) {
        if (!(p.getFirst().getElement() instanceof PsiExpression)) {
          final LinkedList<PsiType> e = new LinkedList<>();
          e.addFirst(p.getSecond());
          myTypeMap.put(p.getFirst(), e);
        }
      }
    }

  }

  public boolean setType(final TypeMigrationUsageInfo usageInfo, @NotNull PsiType type) {
    final LinkedList<PsiType> t = myTypeMap.get(usageInfo);

    final PsiElement element = usageInfo.getElement();

    if (type instanceof PsiEllipsisType && !(element instanceof PsiParameter && ((PsiParameter)element).getDeclarationScope() instanceof PsiMethod)) {
      type = ((PsiEllipsisType)type).toArrayType();
    }

    if (t != null) {
      if (!t.getFirst().equals(type)) {
        if (element instanceof PsiVariable || element instanceof PsiMethod) {
          return false;
        }

        t.addFirst(type);

        return true;
      }
    }
    else {
      final LinkedList<PsiType> e = new LinkedList<>();

      e.addFirst(type);

      usageInfo.setOwnerRoot(myLabeler.getCurrentRoot());
      myTypeMap.put(usageInfo, e);
      return true;
    }

    return false;
  }

  @Nullable
  public PsiType getType(PsiElement element) {
    for (Map.Entry<TypeMigrationUsageInfo, LinkedList<PsiType>> entry : myTypeMap.entrySet()) {
      if (Comparing.equal(element, entry.getKey().getElement())) {
        return entry.getValue().getFirst();
      }
    }
    if (element.getTextRange() == null) return null;
    return getType(new TypeMigrationUsageInfo(element));
  }

  @Nullable
  public PsiType getType(final TypeMigrationUsageInfo usageInfo) {
    final LinkedList<PsiType> e = myTypeMap.get(usageInfo);

    if (e != null) {
      return e.getFirst();
    }

    return TypeMigrationLabeler.getElementType(usageInfo.getElement());
  }

  @Nullable
  public PsiType evaluateType(final PsiExpression expr) {
    if (expr == null) return null;
    final LinkedList<PsiType> e = myTypeMap.get(new TypeMigrationUsageInfo(expr));

    if (e != null) {
      return e.getFirst();
    }

    if (expr instanceof PsiArrayAccessExpression) {
      final PsiType at = evaluateType(((PsiArrayAccessExpression)expr).getArrayExpression());

      if (at instanceof PsiArrayType) {
        return ((PsiArrayType)at).getComponentType();
      }
    }
    else if (expr instanceof PsiAssignmentExpression) {
      return evaluateType(((PsiAssignmentExpression)expr).getLExpression());
    }
    else if (expr instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression call = (PsiMethodCallExpression)expr;
      final JavaResolveResult resolveResult = call.resolveMethodGenerics();
      final PsiMethod method = (PsiMethod)resolveResult.getElement();

      if (method != null) {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        final PsiExpression[] actualParms = call.getArgumentList().getExpressions();
        return PsiUtil.captureToplevelWildcards(createMethodSubstitution(parameters, actualParms, method, call, resolveResult.getSubstitutor(), false).substitute(evaluateType(call.getMethodExpression())), expr);
      }
    }
    else if (expr instanceof PsiPolyadicExpression) {
      final PsiExpression[] operands = ((PsiPolyadicExpression)expr).getOperands();
      final IElementType sign = ((PsiPolyadicExpression)expr).getOperationTokenType();
      PsiType lType = operands.length > 0 ? evaluateType(operands[0]) : null;
      for (int i = 1; i < operands.length; i++) {
        lType = TypeConversionUtil.calcTypeForBinaryExpression(lType, evaluateType(operands[i]), sign, true);
      }
      return lType;
    }
    else if (expr instanceof PsiPostfixExpression) {
      return evaluateType(((PsiPostfixExpression)expr).getOperand());
    }
    else if (expr instanceof PsiPrefixExpression) {
      return evaluateType(((PsiPrefixExpression)expr).getOperand());
    }
    else if (expr instanceof PsiParenthesizedExpression) {
      return evaluateType(((PsiParenthesizedExpression)expr).getExpression());
    }
    else if (expr instanceof PsiConditionalExpression) {
      final PsiExpression thenExpression = ((PsiConditionalExpression)expr).getThenExpression();
      final PsiExpression elseExpression = ((PsiConditionalExpression)expr).getElseExpression();

      final PsiType thenType = evaluateType(thenExpression);
      final PsiType elseType = evaluateType(elseExpression);

      switch ((thenType == null ? 0 : 1) + (elseType == null ? 0 : 2)) {
        case 0:
          return expr.getType();

        case 1:
          return thenType;

        case 2:
          return elseType;

        case 3:
          if (TypeConversionUtil.areTypesConvertible(thenType, elseType)) {
            return thenType;
          }
          else if (TypeConversionUtil.areTypesConvertible(elseType, thenType)) {
            return elseType;
          } else {
            switch ((thenType.equals(thenExpression.getType()) ? 0 : 1) + (elseType.equals(elseExpression.getType()) ? 0 : 2)) {
              case 0:
                return expr.getType();

              case 1:
                return thenType;

              case 2:
                return elseType;

              case 3:
                return expr.getType();

              default:
                LOG.error("Must not happen.");
                return null;
            }
          }

        default:
          LOG.error("Must not happen.");
      }

    }
    else if (expr instanceof PsiNewExpression) {
      final PsiExpression qualifier = ((PsiNewExpression)expr).getQualifier();

      if (qualifier != null) {
        final PsiClassType.ClassResolveResult qualifierResult = resolveType(evaluateType(qualifier));

        if (qualifierResult.getElement() != null) {
          final PsiSubstitutor qualifierSubs = qualifierResult.getSubstitutor();
          final PsiClassType.ClassResolveResult result = resolveType(expr.getType());

          if (result.getElement() != null) {
            final PsiClass aClass = result.getElement();

            return JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory()
                .createType(aClass, result.getSubstitutor().putAll(qualifierSubs));
          }
        }
      }
    }
    else if (expr instanceof PsiFunctionalExpression) {
      final PsiType functionalInterfaceType = ((PsiFunctionalExpression)expr).getFunctionalInterfaceType();
      if (functionalInterfaceType != null) {
        return functionalInterfaceType;
      }
    }
    else if (expr instanceof PsiReferenceExpression) {
      final PsiType type = evaluateReferenceExpressionType(expr);
      if (type != null) {
        return PsiImplUtil.normalizeWildcardTypeByPosition(type, expr);
      }
    }
    else if (expr instanceof PsiSuperExpression) {
      final PsiClass psiClass = PsiTreeUtil.getParentOfType(expr, PsiClass.class);
      if (psiClass != null) {
        final PsiClass superClass = psiClass.getSuperClass();
        if (superClass != null) {
          return getType(new TypeMigrationUsageInfo(superClass));
        }
      }
    }

    return getType(expr);
  }

  @Nullable
  private PsiType evaluateReferenceExpressionType(PsiExpression expr) {
    final PsiReferenceExpression ref = (PsiReferenceExpression)expr;
    final PsiExpression qualifier = ref.getQualifierExpression();

    if (qualifier == null) {
      final PsiElement resolvee = ref.resolve();

      if (resolvee == null) {
        return null;
      }

      return resolvee instanceof PsiClass ? JavaPsiFacade.getElementFactory(resolvee.getProject()).createType((PsiClass)resolvee, PsiSubstitutor.EMPTY) : getType(resolvee);
    }
    else {
      final PsiType qualifierType = evaluateType(qualifier);
      if (!(qualifierType instanceof PsiArrayType)) {
        final PsiElement element = ref.resolve();

        final PsiClassType.ClassResolveResult result = resolveType(qualifierType);

        final PsiClass aClass = result.getElement();
        if (aClass != null) {
          final PsiSubstitutor aSubst = result.getSubstitutor();
          if (element instanceof PsiField) {
            final PsiField field = (PsiField)element;
            PsiType aType = field.getType();
            final PsiClass superClass = field.getContainingClass();
            if (InheritanceUtil.isInheritorOrSelf(aClass, superClass, true)) {
              aType = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY).substitute(aType);
            }
            return aSubst.substitute(aType);
          }  else if (element instanceof PsiMethod){
            final PsiMethod method = (PsiMethod)element;
            PsiType aType = method.getReturnType();
            final PsiClass superClass = method.getContainingClass();
            if (InheritanceUtil.isInheritorOrSelf(aClass, superClass, true)) {
              aType = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY).substitute(aType);
            } else if (InheritanceUtil.isInheritorOrSelf(superClass, aClass, true)) {
              final PsiMethod[] methods = method.findSuperMethods(aClass);
              if (methods.length > 0) {
                aType = methods[0].getReturnType();
              }
            /*}
            final Pair<PsiType, PsiType> pair = myRules.bindTypeParameters(qualifier.getType(), qualifierType, method, ref, myLabeler);
            if (pair != null) {
              final PsiClass psiClass = PsiUtil.resolveClassInType(aType);
              if (psiClass instanceof PsiTypeParameter) {
                aSubst = aSubst.put((PsiTypeParameter)psiClass, pair.getSecond());
              }*/
            }
            return aSubst.substitute(aType);
          }
        }
      }
    }
    return null;
  }

  public static PsiClassType.ClassResolveResult resolveType(PsiType type) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(type);
    final PsiClass aClass = resolveResult.getElement();
    if (aClass instanceof PsiAnonymousClass) {
      final PsiClassType baseClassType = ((PsiAnonymousClass)aClass).getBaseClassType();
      return resolveType(resolveResult.getSubstitutor().substitute(baseClassType));
    }
    return resolveResult;
  }

  public PsiSubstitutor createMethodSubstitution(final PsiParameter[] parameters, final PsiExpression[] actualParms, final PsiMethod method,
                                                 final PsiExpression call) {
    return createMethodSubstitution(parameters, actualParms, method, call, PsiSubstitutor.EMPTY, false);
  }

  public PsiSubstitutor createMethodSubstitution(final PsiParameter[] parameters,
                                                 final PsiExpression[] actualParms,
                                                 final PsiMethod method,
                                                 final PsiExpression call,
                                                 PsiSubstitutor subst,
                                                 boolean preferSubst) {
    final SubstitutorBuilder substitutorBuilder = new SubstitutorBuilder(method, call, subst);

    for (int i = 0; i < Math.min(parameters.length, actualParms.length); i++) {
      substitutorBuilder.bindTypeParameters(getType(parameters[i]), evaluateType(actualParms[i]));
    }
    return substitutorBuilder.createSubstitutor(preferSubst);
  }

  public String getReport() {
    final StringBuffer buffer = new StringBuffer();

    final String[] t = new String[myTypeMap.size()];
    int k = 0;

    for (final TypeMigrationUsageInfo info : myTypeMap.keySet()) {
      final LinkedList<PsiType> types = myTypeMap.get(info);
      final StringBuffer b = new StringBuffer();

      if (types != null) {
        b.append(info.getElement()).append(" : ");

        b.append(StringUtil.join(types, psiType -> psiType.getCanonicalText(), " "));

        b.append("\n");
      }

      t[k++] = b.toString();
    }

    Arrays.sort(t);

    for (String aT : t) {
      buffer.append(aT);
    }

    return buffer.toString();
  }

  public LinkedList<Pair<TypeMigrationUsageInfo, PsiType>> getMigratedDeclarations() {
    final LinkedList<Pair<TypeMigrationUsageInfo, PsiType>> list = new LinkedList<>();

    for (final TypeMigrationUsageInfo usageInfo : myTypeMap.keySet()) {
      final LinkedList<PsiType> types = myTypeMap.get(usageInfo);
      final PsiElement element = usageInfo.getElement();
      if (element instanceof PsiVariable || element instanceof PsiMethod) {
        list.addLast(Pair.create(usageInfo, types.getFirst()));
      }
    }

    return list;
  }

  @Nullable
  static PsiType substituteType(final PsiType migrationType, final PsiType originalType, boolean captureWildcard, PsiClass originalClass, final PsiType rawTypeToReplace) {
    if (originalClass != null) {
      if (((PsiClassType)originalType).hasParameters() && ((PsiClassType)migrationType).hasParameters()) {
        final PsiResolveHelper psiResolveHelper = JavaPsiFacade.getInstance(originalClass.getProject()).getResolveHelper();

        final PsiType rawOriginalType = JavaPsiFacade.getElementFactory(originalClass.getProject()).createType(originalClass, PsiSubstitutor.EMPTY);

        PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
        for (PsiTypeParameter parameter : originalClass.getTypeParameters()) {
          final PsiType type = psiResolveHelper.getSubstitutionForTypeParameter(parameter, rawOriginalType, migrationType, false, PsiUtil.getLanguageLevel(originalClass));
          if (type != null) {
            substitutor = substitutor.put(parameter, captureWildcard && type instanceof PsiWildcardType ? ((PsiWildcardType)type).getExtendsBound() : type);
          } else {
            return null;
          }
        }

        return substitutor.substitute(rawTypeToReplace);
      } else {
        return originalType;
      }
    }
    return null;
  }

  public static PsiType substituteType(final PsiType migrationTtype, final PsiType originalType, final boolean isContraVariantPosition) {
    if ( originalType instanceof PsiClassType && migrationTtype instanceof PsiClassType) {
      final PsiClass originalClass = ((PsiClassType)originalType).resolve();
      if (originalClass != null) {
        if (isContraVariantPosition && TypeConversionUtil.erasure(originalType).isAssignableFrom(TypeConversionUtil.erasure(migrationTtype))) {
          final PsiClass psiClass = ((PsiClassType)migrationTtype).resolve();
          final PsiSubstitutor substitutor = psiClass != null ? TypeConversionUtil.getClassSubstitutor(originalClass, psiClass, PsiSubstitutor.EMPTY) : null;
          if (substitutor != null) {
            final PsiType psiType =
                substituteType(migrationTtype, originalType, false, psiClass, JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(originalClass, substitutor));
            if (psiType != null) {
              return psiType;
            }
          }
        }
        else if (!isContraVariantPosition && TypeConversionUtil.erasure(migrationTtype).isAssignableFrom(TypeConversionUtil.erasure(originalType))) {
          final PsiType psiType = substituteType(migrationTtype, originalType, false, originalClass, JavaPsiFacade.getElementFactory(originalClass.getProject()).createType(originalClass, PsiSubstitutor.EMPTY));
          if (psiType != null) {
            return psiType;
          }
        }
      }
    }
    return migrationTtype;
  }

  @Nullable
  public <T> T getSettings(Class<T> aClass) {
    return myRules.getConversionSettings(aClass);
  }

  private class SubstitutorBuilder {
    private final Map<PsiTypeParameter, PsiType> myMapping;
    private final PsiMethod myMethod;
    private final PsiExpression myCall;
    private final PsiSubstitutor mySubst;


    public SubstitutorBuilder(PsiMethod method, PsiExpression call, PsiSubstitutor subst) {
      mySubst = subst;
      myMapping = new HashMap<>();
      myMethod = method;
      myCall = call;
    }

    private void update(final PsiTypeParameter p, PsiType t) {
      if (t instanceof PsiPrimitiveType) {
        t = ((PsiPrimitiveType)t).getBoxedType(myMethod);
      }
      final PsiType binding = myMapping.get(p);

      if (binding == null) {
        myMapping.put(p, t);
      }
      else if (t != null) {
        myMapping.put(p, PsiIntersectionType.createIntersection(binding, t));
      }
    }

    void bindTypeParameters(PsiType formal, final PsiType actual) {
      if (formal instanceof PsiWildcardType) {
        if (actual instanceof PsiCapturedWildcardType &&
            ((PsiWildcardType)formal).isExtends() == ((PsiCapturedWildcardType)actual).getWildcard().isExtends()) {
          bindTypeParameters(((PsiWildcardType)formal).getBound(), ((PsiCapturedWildcardType)actual).getWildcard().getBound());
          return;
        } else {
          formal = ((PsiWildcardType)formal).getBound();
        }
      }

      if (formal instanceof PsiArrayType && actual instanceof PsiArrayType) {
        bindTypeParameters(((PsiArrayType)formal).getComponentType(), ((PsiArrayType)actual).getComponentType());
        return;
      }

      final Pair<PsiType, PsiType> typePair = myRules.bindTypeParameters(formal, actual, myMethod, myCall, myLabeler);
      if (typePair != null) {
        bindTypeParameters(typePair.getFirst(), typePair.getSecond());
        return;
      }

      final PsiClassType.ClassResolveResult resultF = resolveType(formal);
      final PsiClass classF = resultF.getElement();
      if (classF != null) {

        if (classF instanceof PsiTypeParameter) {
          update((PsiTypeParameter)classF, actual);
          return;
        }

        final PsiClassType.ClassResolveResult resultA = resolveType(actual);

        final PsiClass classA = resultA.getElement();
        if (classA == null) {
          return;
        }


        if (!classA.equals(classF)) {
          final PsiSubstitutor superClassSubstitutor =
              TypeConversionUtil.getClassSubstitutor(classF, classA, resultA.getSubstitutor());
          if (superClassSubstitutor != null) {
            final PsiType aligned = JavaPsiFacade.getInstance(classF.getProject()).getElementFactory().createType(classF, superClassSubstitutor);
            bindTypeParameters(formal, aligned);
          }
        }

        final PsiTypeParameter[] typeParms = classA.getTypeParameters();
        final PsiSubstitutor substA = resultA.getSubstitutor();
        final PsiSubstitutor substF = resultF.getSubstitutor();

        for (PsiTypeParameter typeParm : typeParms) {
          bindTypeParameters(substF.substitute(typeParm), substA.substitute(typeParm));
        }
      }
    }

    public PsiSubstitutor createSubstitutor(boolean preferSubst) {
      PsiSubstitutor theSubst = mySubst;
      if (preferSubst) {
        myMapping.keySet().removeAll(mySubst.getSubstitutionMap().keySet());
      }
      for (final PsiTypeParameter parm : myMapping.keySet()) {
        theSubst = theSubst.put(parm, myMapping.get(parm));
      }
      return theSubst;
    }
  }
}
