/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * Created by IntelliJ IDEA.
 * User: igork
 * Date: Nov 25, 2002
 * Time: 2:05:49 PM
 * To change this template use Options | File Templates.
 */
package com.intellij.psi.scope.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.infos.ClassCandidateInfo;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.MethodProcessorSetupFailedException;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.MethodsProcessor;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;

public class PsiScopesUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.scope.util.PsiScopesUtil");

  private PsiScopesUtil() {
  }

  public static boolean treeWalkUp(@NotNull PsiScopeProcessor processor, @NotNull PsiElement entrance, @Nullable PsiElement maxScope) {
    return treeWalkUp(processor, entrance, maxScope, ResolveState.initial());
  }

  public static boolean treeWalkUp(@NotNull final PsiScopeProcessor processor, @NotNull final PsiElement entrance,
                                    @Nullable final PsiElement maxScope,
                                    @NotNull final ResolveState state) {
    PsiElement prevParent = entrance;
    PsiElement scope = entrance;

    while(scope != null){
      if(scope instanceof PsiClass){
        processor.handleEvent(JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT, scope);
      }
      if (!scope.processDeclarations(processor, state, prevParent, entrance)) {
        return false; // resolved
      }

      if (scope instanceof PsiModifierListOwner && !(scope instanceof PsiParameter/* important for not loading tree! */)){
        PsiModifierList modifierList = ((PsiModifierListOwner)scope).getModifierList();
        if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.STATIC)){
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

  public static boolean walkChildrenScopes(PsiElement thisElement, PsiScopeProcessor processor, ResolveState state, PsiElement lastParent, PsiElement place) {
    PsiElement child = null;
    if (lastParent != null && lastParent.getParent() == thisElement){
      child = lastParent.getPrevSibling();
      if (child == null) return true; // first element
    }

    if (child == null){
      child = thisElement.getLastChild();
    }

    while(child != null){
      if (!child.processDeclarations(processor, state, null, place)) return false;
      child = child.getPrevSibling();
    }

    return true;
  }

  public static boolean resolveAndWalk(PsiScopeProcessor processor, PsiJavaCodeReferenceElement ref, PsiElement maxScope) {
    return resolveAndWalk(processor, ref, maxScope, false);
  }

  public static boolean resolveAndWalk(PsiScopeProcessor processor, PsiJavaCodeReferenceElement ref, PsiElement maxScope, boolean incompleteCode) {
    final PsiElement qualifier = ref.getQualifier();
    final PsiElement classNameElement = ref.getReferenceNameElement();
    if(classNameElement == null) return true;
    if (qualifier != null){
      // Composite expression
      final PsiElementFactory factory = JavaPsiFacade.getInstance(ref.getProject()).getElementFactory();
      PsiElement target = null;
      PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
      if (qualifier instanceof PsiExpression || qualifier instanceof PsiJavaCodeReferenceElement){
        PsiType type = null;
        if(qualifier instanceof PsiExpression){
          type = ((PsiExpression)qualifier).getType();
          final ClassCandidateInfo result = TypeConversionUtil.splitType(type, qualifier);
          if (result != null) {
            target = result.getElement();
            substitutor = result.getSubstitutor();
          }
        }

        if(type == null && qualifier instanceof PsiJavaCodeReferenceElement) {
          // In case of class qualifier
          final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)qualifier;
          final JavaResolveResult result = referenceElement.advancedResolve(incompleteCode);
          target = result.getElement();
          substitutor = result.getSubstitutor();

          if(target instanceof PsiVariable){
            type = substitutor.substitute(((PsiVariable) target).getType());
            if(type instanceof PsiClassType){
              final JavaResolveResult typeResult = ((PsiClassType) type).resolveGenerics();
              target = typeResult.getElement();
              substitutor = substitutor.putAll(typeResult.getSubstitutor());
            }
            else target = null;
          }
          else if(target instanceof PsiMethod){
            type = substitutor.substitute(((PsiMethod) target).getReturnType());
            if(type instanceof PsiClassType){
              final JavaResolveResult typeResult = ((PsiClassType) type).resolveGenerics();
              target = typeResult.getElement();
              substitutor = substitutor.putAll(typeResult.getSubstitutor());
            }
            else target = null;
            final PsiType[] types = referenceElement.getTypeParameters();
            if(target instanceof PsiClass) {
              substitutor = substitutor.putAll((PsiClass)target, types);
            }
          }
          else if(target instanceof PsiClass){
            processor.handleEvent(JavaScopeProcessorEvent.START_STATIC, null);
          }
        }
      }

      if(target != null) return target.processDeclarations(processor, ResolveState.initial().put(PsiSubstitutor.KEY, substitutor), target, ref);
    }
    else{
      // simple expression -> trying to resolve variable or method
      return treeWalkUp(processor, ref, maxScope);
    }

    return true;
  }

  public static void setupAndRunProcessor(MethodsProcessor processor, PsiCallExpression call, boolean dummyImplicitConstructor)
  throws MethodProcessorSetupFailedException{
    if (call instanceof PsiMethodCallExpression){
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)call;
      final PsiJavaCodeReferenceElement ref = methodCall.getMethodExpression();

      processor.setArgumentList(methodCall.getArgumentList());
      processor.obtainTypeArguments(methodCall);
      if (!ref.isQualified() || ref.getReferenceNameElement() instanceof PsiKeyword){
        final PsiElement referenceNameElement = ref.getReferenceNameElement();
        if (referenceNameElement == null) return;
        if (referenceNameElement instanceof PsiKeyword){
          final PsiKeyword keyword = (PsiKeyword)referenceNameElement;

          if (keyword.getTokenType() == JavaTokenType.THIS_KEYWORD){
            final PsiClass aClass = JavaResolveUtil.getContextClass(methodCall);
            if (aClass == null) {
              throw new MethodProcessorSetupFailedException("Can't resolve class for this expression");
            }

            processor.setIsConstructor(true);
            processor.setAccessClass(aClass);
            aClass.processDeclarations(processor, ResolveState.initial(), null, call);

            if (dummyImplicitConstructor){
              processDummyConstructor(processor, aClass);
            }
          }
          else if (keyword.getTokenType() == JavaTokenType.SUPER_KEYWORD){
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
              for (int i = contextSubstitutors.size()-1; i>=0; i--) {
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
          else{
            LOG.error("Unknown name element " + referenceNameElement + " in reference " + ref.getText() + "(" + ref + ")");
          }
        }
        else if (referenceNameElement instanceof PsiIdentifier){
          processor.setIsConstructor(false);
          processor.setName(referenceNameElement.getText());
          processor.setAccessClass(null);
          resolveAndWalk(processor, ref, null);
        }
        else{
          LOG.error("Unknown name element " + referenceNameElement + " in reference " + ref.getText() + "(" + ref + ")");
        }
      }
      else{
        // Complex expression
        final PsiElement referenceName = methodCall.getMethodExpression().getReferenceNameElement();
        final PsiManager manager = call.getManager();
        final PsiElement qualifier = ref.getQualifier();

        if (referenceName instanceof PsiIdentifier && qualifier instanceof PsiExpression){
          PsiType type = ((PsiExpression) qualifier).getType();
          if (type == null) {
            if (qualifier instanceof PsiJavaCodeReferenceElement) {
              final JavaResolveResult result = ((PsiJavaCodeReferenceElement) qualifier).advancedResolve(false);
              if (result.getElement() instanceof PsiClass) {
                processor.handleEvent(JavaScopeProcessorEvent.START_STATIC, null);
                processQualifierResult(result, processor, methodCall);
              }
            }
            else {
              throw new MethodProcessorSetupFailedException("Cant determine qualifier type!");
            }
          } else if (type instanceof PsiIntersectionType) {
            final PsiType[] conjuncts = ((PsiIntersectionType)type).getConjuncts();
            for (PsiType conjunct : conjuncts) {
              if (!processQualifierType(conjunct, processor, manager, methodCall)) break;
            }
          } else {
            processQualifierType(type, processor, manager, methodCall);
          }
        }
        else{
          LOG.assertTrue(false);
        }
      }
    } else{
      LOG.assertTrue(call instanceof PsiNewExpression);
      PsiNewExpression newExpr = (PsiNewExpression)call;
      PsiJavaCodeReferenceElement classRef = newExpr.getClassOrAnonymousClassReference();
      if (classRef == null) {
        throw new MethodProcessorSetupFailedException("Cant get reference to class in new expression");
      }

      final JavaResolveResult result = classRef.advancedResolve(false);
      PsiClass aClass = (PsiClass) result.getElement();
      if (aClass == null)
        throw new MethodProcessorSetupFailedException("Cant resolve class in new expression");
      processor.setIsConstructor(true);
      processor.setAccessClass(aClass);
      processor.setArgumentList(newExpr.getArgumentList());
      processor.obtainTypeArguments(newExpr);
      aClass.processDeclarations(processor, ResolveState.initial().put(PsiSubstitutor.KEY, result.getSubstitutor()), null, call);

      if (dummyImplicitConstructor){
        processDummyConstructor(processor, aClass);
      }
    }
  }

  private static boolean processQualifierType(final PsiType type,
                                         final MethodsProcessor processor,
                                         PsiManager manager,
                                         PsiMethodCallExpression call) throws MethodProcessorSetupFailedException {
    if (type instanceof PsiClassType) {
      JavaResolveResult qualifierResult = ((PsiClassType)type).resolveGenerics();
      return processQualifierResult(qualifierResult, processor, call);
    }
    else if (type instanceof PsiArrayType) {
      LanguageLevel languageLevel = PsiUtil.getLanguageLevel(call);
      JavaResolveResult qualifierResult = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().getArrayClassType(((PsiArrayType)type).getComponentType(),
                                                                                        languageLevel).resolveGenerics();
      return processQualifierResult(qualifierResult, processor, call);
    }
    else if (type instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)type).getConjuncts()) {
        if (!processQualifierType(conjunct, processor, manager, call)) return false;
      }
    }

    return true;
  }

  private static boolean processQualifierResult(JavaResolveResult qualifierResult,
                                           final MethodsProcessor processor,
                                           PsiMethodCallExpression methodCall) throws MethodProcessorSetupFailedException {
    PsiElement resolve = qualifierResult.getElement();

    if (resolve == null)
      throw new MethodProcessorSetupFailedException("Cant determine qualifier class!");

    if (resolve instanceof PsiTypeParameter) {
      processor.setAccessClass((PsiClass)resolve);
    }
    else if (resolve instanceof PsiClass) {
      PsiExpression qualifier = methodCall.getMethodExpression().getQualifierExpression();
      if (!(qualifier instanceof PsiSuperExpression)) {
        processor.setAccessClass((PsiClass)PsiUtil.getAccessObjectClass(qualifier).getElement());
      }
    }

    processor.setIsConstructor(false);
    processor.setName(methodCall.getMethodExpression().getReferenceName());
    return resolve.processDeclarations(processor, ResolveState.initial().put(PsiSubstitutor.KEY, qualifierResult.getSubstitutor()), methodCall, methodCall);
  }

  private static void processDummyConstructor(MethodsProcessor processor, PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) return;
    try{
      PsiMethod[] methods = aClass.getMethods();
      for (PsiMethod method : methods) {
        if (method.isConstructor()) {
          return;
        }
      }
      final PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
      final PsiMethod dummyConstructor = factory.createConstructor();
      if(aClass.getNameIdentifier() != null){
        dummyConstructor.getNameIdentifier().replace(aClass.getNameIdentifier());
      }
      processor.forceAddResult(dummyConstructor);
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
  }
}
