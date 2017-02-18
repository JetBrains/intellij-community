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

package com.intellij.psi.scope.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.infos.ClassCandidateInfo;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.MethodProcessorSetupFailedException;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.MethodsProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PsiScopesUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.scope.util.PsiScopesUtil");

  private PsiScopesUtil() {
  }

  public static boolean treeWalkUp(@NotNull PsiScopeProcessor processor,
                                   @NotNull PsiElement entrance,
                                   @Nullable PsiElement maxScope) {
    return treeWalkUp(processor, entrance, maxScope, ResolveState.initial());
  }

  public static boolean treeWalkUp(@NotNull final PsiScopeProcessor processor,
                                   @NotNull final PsiElement entrance,
                                   @Nullable final PsiElement maxScope,
                                   @NotNull final ResolveState state) {
    if (!entrance.isValid()) {
      LOG.error(new PsiInvalidElementAccessException(entrance));
    }
    PsiElement prevParent = entrance;
    PsiElement scope = entrance;

    while (scope != null) {
      ProgressIndicatorProvider.checkCanceled();
      if (scope instanceof PsiClass) {
        processor.handleEvent(JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT, scope);
      }
      if (!scope.processDeclarations(processor, state, prevParent, entrance)) {
        return false; // resolved
      }

      if (scope instanceof PsiModifierListOwner && !(scope instanceof PsiParameter/* important for not loading tree! */)) {
        PsiModifierList modifierList = ((PsiModifierListOwner)scope).getModifierList();
        if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.STATIC)) {
          processor.handleEvent(JavaScopeProcessorEvent.START_STATIC, null);
        }
      }
      if (scope == maxScope) break;
      prevParent = scope;
      scope = prevParent.getContext();
      processor.handleEvent(JavaScopeProcessorEvent.CHANGE_LEVEL, null);
    }

    return true;
  }

  public static boolean walkChildrenScopes(@NotNull PsiElement thisElement,
                                           @NotNull PsiScopeProcessor processor,
                                           @NotNull ResolveState state,
                                           PsiElement lastParent,
                                           PsiElement place) {
    PsiElement child = null;
    if (lastParent != null && lastParent.getParent() == thisElement) {
      child = lastParent.getPrevSibling();
      if (child == null) return true; // first element
    }

    if (child == null) {
      child = thisElement.getLastChild();
    }

    while (child != null) {
      if (!child.processDeclarations(processor, state, null, place)) return false;
      child = child.getPrevSibling();
    }

    return true;
  }

  public static void processTypeDeclarations(PsiType type, PsiElement place, PsiScopeProcessor processor) {
    if (type instanceof PsiArrayType) {
      LanguageLevel languageLevel = PsiUtil.getLanguageLevel(place);
      final PsiClass arrayClass = JavaPsiFacade.getInstance(place.getProject()).getElementFactory().getArrayClass(languageLevel);
      final PsiTypeParameter[] arrayTypeParameters = arrayClass.getTypeParameters();
      PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
      if (arrayTypeParameters.length > 0) {
        substitutor = substitutor.put(arrayTypeParameters[0], ((PsiArrayType)type).getComponentType());
      }
      arrayClass.processDeclarations(processor, ResolveState.initial().put(PsiSubstitutor.KEY, substitutor), arrayClass, place);
    }
    else if (type instanceof PsiIntersectionType) {
      for (PsiType psiType : ((PsiIntersectionType)type).getConjuncts()) {
        processTypeDeclarations(psiType, place, processor);
      }
    }
    else if (type instanceof PsiDisjunctionType) {
      final PsiType lub = ((PsiDisjunctionType)type).getLeastUpperBound();
      processTypeDeclarations(lub, place, processor);
    }
    else if (type instanceof PsiCapturedWildcardType) {
      final PsiType classType = convertToTypeParameter((PsiCapturedWildcardType)type, place);
      if (classType != null) {
        processTypeDeclarations(classType, place, processor);
      }
    }
    else {
      final JavaResolveResult result = PsiUtil.resolveGenericsClassInType(type);
      final PsiClass clazz = (PsiClass)result.getElement();
      if (clazz != null) {
        clazz.processDeclarations(processor, ResolveState.initial().put(PsiSubstitutor.KEY, result.getSubstitutor()), clazz, place);
      }
    }
  }

  public static boolean resolveAndWalk(@NotNull PsiScopeProcessor processor,
                                       @NotNull PsiJavaCodeReferenceElement ref,
                                       @Nullable PsiElement maxScope) {
    return resolveAndWalk(processor, ref, maxScope, false);
  }

  public static boolean resolveAndWalk(@NotNull PsiScopeProcessor processor,
                                       @NotNull PsiJavaCodeReferenceElement ref,
                                       @Nullable PsiElement maxScope,
                                       boolean incompleteCode) {
    final PsiElement qualifier = ref.getQualifier();
    final PsiElement classNameElement = ref.getReferenceNameElement();
    if (classNameElement == null) return true;
    if (qualifier != null) {
      // Composite expression
      PsiElement target = null;
      PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
      if (qualifier instanceof PsiExpression || qualifier instanceof PsiJavaCodeReferenceElement) {
        PsiType type = null;
        if (qualifier instanceof PsiExpression) {
          type = ((PsiExpression)qualifier).getType();
          if (type != null) {
            assert type.isValid() : type.getClass() + "; " + qualifier;
          }
          processTypeDeclarations(type, ref, processor);
        }

        if (type == null && qualifier instanceof PsiJavaCodeReferenceElement) {
          // In case of class qualifier
          final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)qualifier;
          final JavaResolveResult result = referenceElement.advancedResolve(incompleteCode);
          target = result.getElement();
          substitutor = result.getSubstitutor();

          if (target instanceof PsiVariable) {
            type = substitutor.substitute(((PsiVariable)target).getType());
            if (type instanceof PsiClassType) {
              final JavaResolveResult typeResult = ((PsiClassType)type).resolveGenerics();
              target = typeResult.getElement();
              substitutor = substitutor.putAll(typeResult.getSubstitutor());
            }
            else {
              target = null;
            }
          }
          else if (target instanceof PsiMethod) {
            type = substitutor.substitute(((PsiMethod)target).getReturnType());
            if (type instanceof PsiClassType) {
              final JavaResolveResult typeResult = ((PsiClassType)type).resolveGenerics();
              target = typeResult.getElement();
              substitutor = substitutor.putAll(typeResult.getSubstitutor());
            }
            else {
              target = null;
            }
            final PsiType[] types = referenceElement.getTypeParameters();
            if (target instanceof PsiClass) {
              substitutor = substitutor.putAll((PsiClass)target, types);
            }
          }
          else if (target instanceof PsiClass) {
            processor.handleEvent(JavaScopeProcessorEvent.START_STATIC, null);
          }
        }
      }

      if (target != null) {
        return target.processDeclarations(processor, ResolveState.initial().put(PsiSubstitutor.KEY, substitutor), target, ref);
      }
    }
    else {
      // simple expression -> trying to resolve variable or method
      return treeWalkUp(processor, ref, maxScope);
    }

    return true;
  }

  public static void setupAndRunProcessor(@NotNull MethodsProcessor processor,
                                          @NotNull PsiCallExpression call,
                                          boolean dummyImplicitConstructor)
  throws MethodProcessorSetupFailedException {
    if (call instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)call;
      final PsiJavaCodeReferenceElement ref = methodCall.getMethodExpression();


      processor.setArgumentList(methodCall.getArgumentList());
      processor.obtainTypeArguments(methodCall);
      if (!ref.isQualified() || ref.getReferenceNameElement() instanceof PsiKeyword) {
        final PsiElement referenceNameElement = ref.getReferenceNameElement();
        if (referenceNameElement == null) return;
        if (referenceNameElement instanceof PsiKeyword) {
          final PsiKeyword keyword = (PsiKeyword)referenceNameElement;

          if (keyword.getTokenType() == JavaTokenType.THIS_KEYWORD) {
            final PsiClass aClass = JavaResolveUtil.getContextClass(methodCall);
            if (aClass == null) {
              throw new MethodProcessorSetupFailedException("Can't resolve class for this expression");
            }

            processor.setIsConstructor(true);
            processor.setAccessClass(aClass);
            aClass.processDeclarations(processor, ResolveState.initial(), null, call);

            if (dummyImplicitConstructor) {
              processDummyConstructor(processor, aClass);
            }
          }
          else if (keyword.getTokenType() == JavaTokenType.SUPER_KEYWORD) {
            PsiClass aClass = JavaResolveUtil.getContextClass(methodCall);
            if (aClass == null) {
              throw new MethodProcessorSetupFailedException("Can't resolve class for super expression");
            }

            final PsiClass superClass = aClass.getSuperClass();
            if (superClass != null) {
              PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
              PsiClass runSuper = superClass;
              List<PsiSubstitutor> contextSubstitutors = new ArrayList<PsiSubstitutor>();
              do {
                if (runSuper != null) {
                  PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(runSuper, aClass, PsiSubstitutor.EMPTY);
                  contextSubstitutors.add(superSubstitutor);
                }
                if (aClass.hasModifierProperty(PsiModifier.STATIC)) break;
                aClass = JavaResolveUtil.getContextClass(aClass);
                if (aClass != null) runSuper = aClass.getSuperClass();
              }
              while (aClass != null);
              //apply substitutors in 'outer classes down to inner classes' order because inner class subst take precedence
              for (int i = contextSubstitutors.size() - 1; i >= 0; i--) {
                PsiSubstitutor contextSubstitutor = contextSubstitutors.get(i);
                substitutor = substitutor.putAll(contextSubstitutor);
              }

              processor.setIsConstructor(true);
              processor.setAccessClass(null);
              final PsiMethod[] constructors = superClass.getConstructors();
              ResolveState state = ResolveState.initial().put(PsiSubstitutor.KEY, substitutor);
              for (PsiMethod constructor : constructors) {
                if (!processor.execute(constructor, state)) return;
              }

              if (dummyImplicitConstructor) processDummyConstructor(processor, superClass);
            }
          }
          else {
            LOG.error("Unknown name element " + referenceNameElement + " in reference " + ref.getText() + "(" + ref + ")");
          }
        }
        else if (referenceNameElement instanceof PsiIdentifier) {
          processor.setIsConstructor(false);
          processor.setName(referenceNameElement.getText());
          processor.setAccessClass(null);
          resolveAndWalk(processor, ref, null);
        }
        else {
          LOG.error("Unknown name element " + referenceNameElement + " in reference " + ref.getText() + "(" + ref + ")");
        }
      }
      else {
        // Complex expression
        final PsiElement referenceName = methodCall.getMethodExpression().getReferenceNameElement();
        final PsiManager manager = call.getManager();
        final PsiElement qualifier = ref.getQualifier();
        if (referenceName == null) {
          // e.g. "manager.(beginTransaction)"
          throw new MethodProcessorSetupFailedException("Can't resolve method name for this expression");
        }
        if (referenceName instanceof PsiIdentifier && qualifier instanceof PsiExpression) {
          PsiType type = ((PsiExpression)qualifier).getType();
          if (type != null && qualifier instanceof PsiReferenceExpression) {
            final PsiElement resolve = ((PsiReferenceExpression)qualifier).resolve();
            if (resolve instanceof PsiEnumConstant) {
              final PsiEnumConstantInitializer initializingClass = ((PsiEnumConstant)resolve).getInitializingClass();
              if (hasDesiredMethod(methodCall, type, initializingClass)) {
                processQualifierResult(new ClassCandidateInfo(initializingClass, PsiSubstitutor.EMPTY), processor, methodCall);
                return;
              }
            }
            else if (resolve instanceof PsiVariable && ((PsiVariable)resolve).hasModifierProperty(PsiModifier.FINAL) && ((PsiVariable)resolve).hasInitializer()) {
              final PsiExpression initializer = ((PsiVariable)resolve).getInitializer();
              if (initializer instanceof PsiNewExpression) {
                final PsiAnonymousClass anonymousClass = ((PsiNewExpression)initializer).getAnonymousClass();
                if (hasDesiredMethod(methodCall, type, anonymousClass)) {
                  type = initializer.getType();
                }
              }
            }
          }
          if (type == null) {
            if (qualifier instanceof PsiJavaCodeReferenceElement) {
              final JavaResolveResult result = ((PsiJavaCodeReferenceElement)qualifier).advancedResolve(false);
              if (result.getElement() instanceof PsiClass) {
                processor.handleEvent(JavaScopeProcessorEvent.START_STATIC, null);
                processQualifierResult(result, processor, methodCall);
              }
            }
            else {
              throw new MethodProcessorSetupFailedException("Cant determine qualifier type!");
            }
          }
          else if (type instanceof PsiDisjunctionType) {
            processQualifierType(((PsiDisjunctionType)type).getLeastUpperBound(), processor, manager, methodCall);
          }
          else if (type instanceof PsiCapturedWildcardType) {
            final PsiType psiType = convertToTypeParameter((PsiCapturedWildcardType)type, methodCall);
            if (psiType != null) {
              processQualifierType(psiType, processor, manager, methodCall);
            }
          }
          else {
            processQualifierType(type, processor, manager, methodCall);
          }
        }
        else {
          LOG.error("ref: " + ref + " (" + ref.getClass() + ")," +
                    " ref.getReferenceNameElement()=" + ref.getReferenceNameElement() +
                    "; methodCall.getMethodExpression().getReferenceNameElement()=" + methodCall.getMethodExpression().getReferenceNameElement() +
                    "; qualifier="+qualifier);
        }
      }
    }
    else {
      LOG.assertTrue(call instanceof PsiNewExpression);
      PsiNewExpression newExpr = (PsiNewExpression)call;
      PsiJavaCodeReferenceElement classRef = newExpr.getClassOrAnonymousClassReference();
      if (classRef == null) {
        throw new MethodProcessorSetupFailedException("Cant get reference to class in new expression");
      }

      final JavaResolveResult result = classRef.advancedResolve(false);
      PsiClass aClass = (PsiClass)result.getElement();
      if (aClass == null) {
        throw new MethodProcessorSetupFailedException("Cant resolve class in new expression");
      }
      processor.setIsConstructor(true);
      processor.setAccessClass(aClass);
      processor.setArgumentList(newExpr.getArgumentList());
      processor.obtainTypeArguments(newExpr);
      aClass.processDeclarations(processor, ResolveState.initial().put(PsiSubstitutor.KEY, result.getSubstitutor()), null, call);

      if (dummyImplicitConstructor) {
        processDummyConstructor(processor, aClass);
      }
    }
  }

  private static PsiType convertToTypeParameter(PsiCapturedWildcardType type, PsiElement methodCall) {
    GlobalSearchScope placeResolveScope = methodCall.getResolveScope();
    PsiType upperBound = PsiClassImplUtil.correctType(type.getUpperBound(), placeResolveScope);
    while (upperBound instanceof PsiCapturedWildcardType) {
      upperBound = PsiClassImplUtil.correctType(((PsiCapturedWildcardType)upperBound).getUpperBound(), placeResolveScope);
    }

    //arrays can't participate in extends list
    if (upperBound instanceof PsiArrayType) {
      return upperBound;
    }

    if (upperBound != null) {
      return InferenceSession.createTypeParameterTypeWithUpperBound(upperBound, methodCall);
    }
    return null;
  }

  private static boolean hasDesiredMethod(PsiMethodCallExpression methodCall, PsiType type, PsiAnonymousClass anonymousClass) {
    if (anonymousClass != null && type.equals(anonymousClass.getBaseClassType())) {
      final PsiMethod[] refMethods = anonymousClass.findMethodsByName(methodCall.getMethodExpression().getReferenceName(), false);
      if (refMethods.length > 0) {
        final PsiClass baseClass = PsiUtil.resolveClassInType(type);
        if (baseClass != null && !hasCovariantOverridingOrNotPublic(baseClass, refMethods)) {
          for (PsiMethod method : refMethods) {
            if (method.findSuperMethods(baseClass).length > 0) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private static boolean hasCovariantOverridingOrNotPublic(PsiClass baseClass, PsiMethod[] refMethods) {
    for (PsiMethod method : refMethods) {
      final PsiType methodReturnType = method.getReturnType();
      for (PsiMethod superMethod : method.findSuperMethods(baseClass)) {
        if (!Comparing.equal(methodReturnType, superMethod.getReturnType())) {
          return true;
        }

        if (!superMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean processQualifierType(@NotNull final PsiType type,
                                              final MethodsProcessor processor,
                                              PsiManager manager,
                                              PsiMethodCallExpression call) throws MethodProcessorSetupFailedException {
    LOG.assertTrue(type.isValid());
    if (type instanceof PsiClassType) {
      JavaResolveResult qualifierResult = ((PsiClassType)type).resolveGenerics();
      return processQualifierResult(qualifierResult, processor, call);
    }
    if (type instanceof PsiArrayType) {
      LanguageLevel languageLevel = PsiUtil.getLanguageLevel(call);
      PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
      JavaResolveResult qualifierResult =
        factory.getArrayClassType(((PsiArrayType)type).getComponentType(), languageLevel).resolveGenerics();
      return processQualifierResult(qualifierResult, processor, call);
    }
    if (type instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)type).getConjuncts()) {
        if (!processQualifierType(conjunct, processor, manager, call)) return false;
      }
    }

    return true;
  }

  private static boolean processQualifierResult(@NotNull JavaResolveResult qualifierResult,
                                                @NotNull MethodsProcessor processor,
                                                @NotNull PsiMethodCallExpression methodCall) throws MethodProcessorSetupFailedException {
    PsiElement resolve = qualifierResult.getElement();

    if (resolve == null) {
      throw new MethodProcessorSetupFailedException("Cant determine qualifier class!");
    }

    if (resolve instanceof PsiTypeParameter) {
      processor.setAccessClass((PsiClass)resolve);
    }
    else if (resolve instanceof PsiClass) {
      PsiExpression qualifier = methodCall.getMethodExpression().getQualifierExpression();
      if (!(qualifier instanceof PsiSuperExpression)) {
        processor.setAccessClass((PsiClass)PsiUtil.getAccessObjectClass(qualifier).getElement());
      }
      else if (((PsiSuperExpression)qualifier).getQualifier() != null && PsiUtil.isLanguageLevel8OrHigher(qualifier) && 
               CommonClassNames.JAVA_LANG_CLONEABLE.equals(((PsiClass)resolve).getQualifiedName()) && ((PsiClass)resolve).isInterface()) {
        processor.setAccessClass((PsiClass)resolve);
      }
    }

    processor.setIsConstructor(false);
    processor.setName(methodCall.getMethodExpression().getReferenceName());
    ResolveState state = ResolveState.initial().put(PsiSubstitutor.KEY, qualifierResult.getSubstitutor());
    return resolve.processDeclarations(processor, state, methodCall, methodCall);
  }

  private static void processDummyConstructor(MethodsProcessor processor, PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) return;
    try {
      PsiMethod[] constructors = aClass.getConstructors();
      if (constructors.length != 0) {
        return;
      }
      final PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
      final PsiMethod dummyConstructor = factory.createConstructor();
      PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
      if (nameIdentifier != null) {
        dummyConstructor.getNameIdentifier().replace(nameIdentifier);
      }
      processor.forceAddResult(dummyConstructor);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
