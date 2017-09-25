/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.refactoring.changeSignature;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableUtil;
import com.intellij.codeInsight.generation.surroundWith.SurroundWithUtil;
import com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.scope.processor.VariablesProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.rename.ResolveSnapshotProvider;
import com.intellij.refactoring.util.*;
import com.intellij.refactoring.util.usageInfo.DefaultConstructorImplicitUsageInfo;
import com.intellij.refactoring.util.usageInfo.NoConstructorClassUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import com.siyeh.IntentionPowerPackBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Maxim.Medvedev
 */
public class JavaChangeSignatureUsageProcessor implements ChangeSignatureUsageProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.changeSignature.JavaChangeSignatureUsageProcessor");

  private static boolean isJavaUsage(UsageInfo info) {
    final PsiElement element = info.getElement();
    if (element == null) return false;
    return element.getLanguage() == StdLanguages.JAVA;
  }

  @Override
  public UsageInfo[] findUsages(ChangeInfo info) {
    if (info instanceof JavaChangeInfo) {
      return new JavaChangeSignatureUsageSearcher((JavaChangeInfo)info).findUsages();
    }
    else {
      return UsageInfo.EMPTY_ARRAY;
    }
  }

  @Override
  public MultiMap<PsiElement, String> findConflicts(ChangeInfo info, Ref<UsageInfo[]> refUsages) {
    if (info instanceof JavaChangeInfo) {
      return new ConflictSearcher((JavaChangeInfo)info).findConflicts(refUsages);
    }
    else {
      return new MultiMap<>();
    }
  }

  @Override
  public boolean processUsage(ChangeInfo changeInfo, UsageInfo usage, boolean beforeMethodChange, UsageInfo[] usages) {
    if (!isJavaUsage(usage)) return false;
    if (!(changeInfo instanceof JavaChangeInfo)) return false;


    if (beforeMethodChange) {
      if (usage instanceof CallerUsageInfo) {
        final CallerUsageInfo callerUsageInfo = (CallerUsageInfo)usage;
        processCallerMethod((JavaChangeInfo)changeInfo, callerUsageInfo.getMethod(), null, callerUsageInfo.isToInsertParameter(),
                            callerUsageInfo.isToInsertException());
        return true;
      }
      else if (usage instanceof OverriderUsageInfo) {
        OverriderUsageInfo info = (OverriderUsageInfo)usage;
        final PsiMethod method = info.getOverridingMethod();
        final PsiMethod baseMethod = info.getBaseMethod();
        if (info.isOriginalOverrider()) {
          processPrimaryMethod((JavaChangeInfo)changeInfo, method, baseMethod, false);
        }
        else {
          processCallerMethod((JavaChangeInfo)changeInfo, method, baseMethod, info.isToInsertArgs(), info.isToCatchExceptions());
        }
        return true;
      }
      else if (usage instanceof MethodReferenceUsageInfo && MethodReferenceUsageInfo.needToExpand((JavaChangeInfo)changeInfo)) {
        final PsiElement element = usage.getElement();
        if (element instanceof PsiMethodReferenceExpression ) {
          final PsiExpression expression = LambdaRefactoringUtil.convertToMethodCallInLambdaBody((PsiMethodReferenceExpression)element);
          if (expression instanceof PsiCallExpression) {
            ((MethodReferenceUsageInfo)usage).setCallExpression((PsiCallExpression)expression);
            return true;
          }
        }
      }
      else if (usage instanceof FunctionalInterfaceChangedUsageInfo) {
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(usage.getProject());
        final PsiElement element = usage.getElement();
        final PsiMethod interfaceMethod = ((FunctionalInterfaceChangedUsageInfo)usage).getMethod();
        if (element instanceof PsiLambdaExpression) {
          processMethodParams((JavaChangeInfo)changeInfo, interfaceMethod,
                              elementFactory, PsiSubstitutor.EMPTY, ((PsiLambdaExpression)element).getParameterList(), ((PsiLambdaExpression)element).getBody());
        }
        else if (element instanceof PsiMethodReferenceExpression) {
          final PsiLambdaExpression lambdaExpression =
            LambdaRefactoringUtil.convertMethodReferenceToLambda((PsiMethodReferenceExpression)element, false, true);
          if (lambdaExpression != null) {
            processMethodParams(((JavaChangeInfo)changeInfo), interfaceMethod, elementFactory, PsiSubstitutor.EMPTY, 
                                lambdaExpression.getParameterList(), lambdaExpression.getBody());
          }
        }
        return true;
      }

    }
    else {
      PsiElement element = usage.getElement();
      LOG.assertTrue(element != null);

      if (usage instanceof DefaultConstructorImplicitUsageInfo) {
        final DefaultConstructorImplicitUsageInfo defConstructorUsage = (DefaultConstructorImplicitUsageInfo)usage;
        PsiMethod constructor = defConstructorUsage.getConstructor();
        if (!constructor.isPhysical()) {
          final boolean toPropagate =
            changeInfo instanceof JavaChangeInfoImpl && ((JavaChangeInfoImpl)changeInfo).propagateParametersMethods.remove(constructor);
          final PsiClass containingClass = defConstructorUsage.getContainingClass();
          constructor = (PsiMethod)containingClass.add(constructor);
          PsiUtil.setModifierProperty(constructor, VisibilityUtil.getVisibilityModifier(containingClass.getModifierList()), true);
          if (toPropagate) {
            ((JavaChangeInfoImpl)changeInfo).propagateParametersMethods.add(constructor);
          }
        }
        addSuperCall((JavaChangeInfo)changeInfo, constructor, defConstructorUsage.getBaseConstructor(), usages);
        return true;
      }
      else if (usage instanceof NoConstructorClassUsageInfo) {
        addDefaultConstructor(((JavaChangeInfo)changeInfo), ((NoConstructorClassUsageInfo)usage).getPsiClass(), usages);
        return true;
      }
      else if (usage instanceof MethodReferenceUsageInfo && MethodReferenceUsageInfo.needToExpand((JavaChangeInfo)changeInfo)) {
        final MethodCallUsageInfo methodCallInfo = ((MethodReferenceUsageInfo)usage).createMethodCallInfo();
        if (methodCallInfo != null) {
          processMethodUsage(methodCallInfo.getElement(), (JavaChangeInfo)changeInfo, methodCallInfo.isToChangeArguments(),
                             methodCallInfo.isToCatchExceptions(), methodCallInfo.getReferencedMethod(), methodCallInfo.getSubstitutor(), usages);
          return true;
        }
      }
      else if (usage instanceof MethodCallUsageInfo) {
        final MethodCallUsageInfo methodCallInfo = (MethodCallUsageInfo)usage;
        processMethodUsage(methodCallInfo.getElement(), (JavaChangeInfo)changeInfo, methodCallInfo.isToChangeArguments(),
                           methodCallInfo.isToCatchExceptions(), methodCallInfo.getReferencedMethod(), methodCallInfo.getSubstitutor(), usages);
        return true;
      }
      else if (usage instanceof ChangeSignatureParameterUsageInfo) {
        String newName = ((ChangeSignatureParameterUsageInfo)usage).newParameterName;
        String oldName = ((ChangeSignatureParameterUsageInfo)usage).oldParameterName;
        processParameterUsage((PsiReferenceExpression)element, oldName, newName);
        return true;
      }
      else if (usage instanceof CallReferenceUsageInfo) {
        ((CallReferenceUsageInfo)usage).getReference().handleChangeSignature(changeInfo);
        return true;
      }
      else if (element instanceof PsiEnumConstant) {
        fixActualArgumentsList(((PsiEnumConstant)element).getArgumentList(), (JavaChangeInfo)changeInfo, true, PsiSubstitutor.EMPTY);
        return true;
      }
      else if (!(usage instanceof OverriderUsageInfo)) {
        PsiReference reference = usage instanceof MoveRenameUsageInfo ? usage.getReference() : element.getReference();
        if (reference != null) {
          PsiElement target = changeInfo.getMethod();
          if (target != null) {
            reference.bindToElement(target);
          }
        }
      }
    }
    return false;
  }

  private static void processParameterUsage(PsiReferenceExpression ref, String oldName, String newName)
    throws IncorrectOperationException {

    PsiElement last = ref.getReferenceNameElement();
    if (last instanceof PsiIdentifier && last.getText().equals(oldName)) {
      PsiElementFactory factory = JavaPsiFacade.getInstance(ref.getProject()).getElementFactory();
      PsiIdentifier newNameIdentifier = factory.createIdentifier(newName);
      last.replace(newNameIdentifier);
    }
  }


  private static void addDefaultConstructor(JavaChangeInfo changeInfo, PsiClass aClass, final UsageInfo[] usages)
    throws IncorrectOperationException {
    if (!(aClass instanceof PsiAnonymousClass)) {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(aClass.getProject());
      PsiMethod defaultConstructor = factory.createMethodFromText(aClass.getName() + "(){}", aClass);
      defaultConstructor = (PsiMethod)CodeStyleManager.getInstance(aClass.getProject()).reformat(defaultConstructor);
      defaultConstructor = (PsiMethod)aClass.add(defaultConstructor);
      PsiUtil.setModifierProperty(defaultConstructor, VisibilityUtil.getVisibilityModifier(aClass.getModifierList()), true);
      addSuperCall(changeInfo, defaultConstructor, null, usages);
    }
    else {
      final PsiElement parent = aClass.getParent();
      if (parent instanceof PsiNewExpression) {
        final PsiExpressionList argumentList = ((PsiNewExpression)parent).getArgumentList();
        final PsiClass baseClass = changeInfo.getMethod().getContainingClass();
        final PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, aClass, PsiSubstitutor.EMPTY);
        fixActualArgumentsList(argumentList, changeInfo, true, substitutor);
      }
    }
  }

  private static void addSuperCall(JavaChangeInfo changeInfo, PsiMethod constructor, PsiMethod callee, final UsageInfo[] usages)
    throws IncorrectOperationException {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(constructor.getProject());
    PsiExpressionStatement superCall = (PsiExpressionStatement)factory.createStatementFromText("super();", constructor);
    PsiCodeBlock body = constructor.getBody();
    assert body != null;
    PsiStatement[] statements = body.getStatements();
    if (statements.length > 0) {
      superCall = (PsiExpressionStatement)body.addBefore(superCall, statements[0]);
    }
    else {
      superCall = (PsiExpressionStatement)body.add(superCall);
    }
    PsiMethodCallExpression callExpression = (PsiMethodCallExpression)superCall.getExpression();
    final PsiClass aClass = constructor.getContainingClass();
    final PsiClass baseClass = changeInfo.getMethod().getContainingClass();
    final PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, aClass, PsiSubstitutor.EMPTY);
    processMethodUsage(callExpression.getMethodExpression(), changeInfo, true, false, callee, substitutor, usages);
  }

  private static void processMethodUsage(PsiElement ref,
                                  JavaChangeInfo changeInfo,
                                  boolean toChangeArguments,
                                  boolean toCatchExceptions,
                                  PsiMethod callee, PsiSubstitutor substitutor, final UsageInfo[] usages) throws IncorrectOperationException {
    if (changeInfo.isNameChanged()) {
      if (ref instanceof PsiJavaCodeReferenceElement) {
        PsiElement last = ((PsiJavaCodeReferenceElement)ref).getReferenceNameElement();
        if (last instanceof PsiIdentifier && last.getText().equals(changeInfo.getOldName())) {
          last.replace(changeInfo.getNewNameIdentifier());
        }
      }
    }

    final PsiMethod caller = RefactoringUtil.getEnclosingMethod(ref);
    if (toChangeArguments) {
      final PsiExpressionList list = RefactoringUtil.getArgumentListByMethodReference(ref);
      LOG.assertTrue(list != null);
      boolean toInsertDefaultValue = needDefaultValue(changeInfo, caller);
      if (toInsertDefaultValue && ref instanceof PsiReferenceExpression) {
        final PsiExpression qualifierExpression = ((PsiReferenceExpression)ref).getQualifierExpression();
        if (qualifierExpression instanceof PsiSuperExpression && caller != null && callerSignatureIsAboutToChangeToo(caller, usages)) {
          toInsertDefaultValue = false;
        }
      }

      fixActualArgumentsList(list, changeInfo, toInsertDefaultValue, substitutor);
    }

    if (toCatchExceptions) {
      if (!(ref instanceof PsiReferenceExpression &&
            JavaHighlightUtil.isSuperOrThisCall(PsiTreeUtil.getParentOfType(ref, PsiStatement.class), true, false))) {
        if (needToCatchExceptions(changeInfo, caller)) {
          PsiClassType[] newExceptions =
            callee != null ? getCalleeChangedExceptionInfo(callee) : getPrimaryChangedExceptionInfo(changeInfo);
          fixExceptions(ref, newExceptions);
        }
      }
    }
  }

  private static boolean callerSignatureIsAboutToChangeToo(@NotNull final PsiMethod caller, final UsageInfo[] usages) {
    for (UsageInfo usage : usages) {
      if (usage instanceof MethodCallUsageInfo &&
          MethodSignatureUtil.isSuperMethod(((MethodCallUsageInfo)usage).getReferencedMethod(), caller)) {
        return true;
      }
    }
    return false;
  }

  private static PsiClassType[] getCalleeChangedExceptionInfo(final PsiMethod callee) {
    return callee.getThrowsList().getReferencedTypes(); //Callee method's throws list is already modified!
  }

  private static void fixExceptions(PsiElement ref, PsiClassType[] newExceptions) throws IncorrectOperationException {
    //methods' throws lists are already modified, may use ExceptionUtil.collectUnhandledExceptions
    newExceptions = filterCheckedExceptions(newExceptions);

    PsiElement context = PsiTreeUtil.getParentOfType(ref, PsiTryStatement.class, PsiMethod.class, PsiLambdaExpression.class);
    if (context instanceof PsiTryStatement) {
      PsiTryStatement tryStatement = (PsiTryStatement)context;
      PsiCodeBlock tryBlock = tryStatement.getTryBlock();

      //Remove unused catches
      Collection<PsiClassType> classes = ExceptionUtil.collectUnhandledExceptions(tryBlock, tryBlock);
      PsiParameter[] catchParameters = tryStatement.getCatchBlockParameters();
      for (PsiParameter parameter : catchParameters) {
        final PsiType caughtType = parameter.getType();

        if (!(caughtType instanceof PsiClassType)) continue;
        if (ExceptionUtil.isUncheckedExceptionOrSuperclass((PsiClassType)caughtType)) continue;

        if (!isCatchParameterRedundant((PsiClassType)caughtType, classes)) continue;
        parameter.getParent().delete(); //delete catch section
      }

      PsiClassType[] exceptionsToAdd = filterUnhandledExceptions(newExceptions, tryBlock);
      addExceptions(exceptionsToAdd, tryStatement);

      adjustPossibleEmptyTryStatement(tryStatement);
    }
    else {
      newExceptions = filterUnhandledExceptions(newExceptions, ref);
      if (newExceptions.length > 0) {
        //Add new try statement
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(ref.getProject());
        PsiTryStatement tryStatement = (PsiTryStatement)elementFactory.createStatementFromText("try {} catch (Exception e) {}", null);
        PsiStatement anchor;
        if (context instanceof PsiLambdaExpression) {
          PsiCodeBlock codeBlock = RefactoringUtil.expandExpressionLambdaToCodeBlock((PsiLambdaExpression)context);
          LOG.assertTrue(codeBlock != null);
          anchor = codeBlock.getStatements()[0];
        }
        else {
          anchor = PsiTreeUtil.getParentOfType(ref, PsiStatement.class);
        }
        LOG.assertTrue(anchor != null);
        PsiElement container = anchor.getParent();
        PsiElement[] elements = SurroundWithUtil.moveDeclarationsOut(container, new PsiElement[]{anchor}, true);
        tryStatement = (PsiTryStatement)container.addAfter(tryStatement, elements[elements.length - 1]);
        PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        LOG.assertTrue(tryBlock != null);
        tryBlock.addRange(elements[0], elements[elements.length - 1]);

        addExceptions(newExceptions, tryStatement);
        container.deleteChildRange(elements[0], elements[elements.length - 1]);
        tryStatement.getCatchSections()[0].delete(); //Delete dummy catch section
      }
    }
  }

  public static boolean hasNewCheckedExceptions(JavaChangeInfo changeInfo) {
    return filterCheckedExceptions(getPrimaryChangedExceptionInfo(changeInfo)).length > 0;
  }

  private static PsiClassType[] filterCheckedExceptions(PsiClassType[] exceptions) {
    List<PsiClassType> result = new ArrayList<>();
    for (PsiClassType exceptionType : exceptions) {
      if (!ExceptionUtil.isUncheckedException(exceptionType)) result.add(exceptionType);
    }
    return result.toArray(new PsiClassType[result.size()]);
  }

  private static void adjustPossibleEmptyTryStatement(PsiTryStatement tryStatement) throws IncorrectOperationException {
    PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (tryBlock != null) {
      if (tryStatement.getCatchSections().length == 0 &&
          tryStatement.getFinallyBlock() == null) {
        PsiElement firstBodyElement = tryBlock.getFirstBodyElement();
        if (firstBodyElement != null) {
          tryStatement.getParent().addRangeAfter(firstBodyElement, tryBlock.getLastBodyElement(), tryStatement);
        }
        tryStatement.delete();
      }
    }
  }

  private static void addExceptions(PsiClassType[] exceptionsToAdd, PsiTryStatement tryStatement) throws IncorrectOperationException {
    for (PsiClassType type : exceptionsToAdd) {
      final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(tryStatement.getProject());
      String name = styleManager.suggestVariableName(VariableKind.PARAMETER, null, null, type).names[0];
      name = styleManager.suggestUniqueVariableName(name, tryStatement, false);

      PsiCatchSection catchSection =
        JavaPsiFacade.getInstance(tryStatement.getProject()).getElementFactory().createCatchSection(type, name, tryStatement);
      tryStatement.add(catchSection);
    }
  }

  private static PsiClassType[] filterUnhandledExceptions(PsiClassType[] exceptions, PsiElement place) {
    List<PsiClassType> result = new ArrayList<>();
    for (PsiClassType exception : exceptions) {
      if (!ExceptionUtil.isHandled(exception, place)) result.add(exception);
    }
    return result.toArray(new PsiClassType[result.size()]);
  }

  private static boolean isCatchParameterRedundant(PsiClassType catchParamType, Collection<PsiClassType> thrownTypes) {
    for (PsiType exceptionType : thrownTypes) {
      if (exceptionType.isConvertibleFrom(catchParamType)) return false;
    }
    return true;
  }

  //This methods works equally well for primary usages as well as for propagated callers' usages
  private static void fixActualArgumentsList(PsiExpressionList list,
                                             JavaChangeInfo changeInfo,
                                             boolean toInsertDefaultValue,
                                             PsiSubstitutor substitutor) throws IncorrectOperationException {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(list.getProject()).getElementFactory();
    if (changeInfo.isParameterSetOrOrderChanged()) {
      if (changeInfo instanceof JavaChangeInfoImpl && ((JavaChangeInfoImpl)changeInfo).isPropagationEnabled) {
        final ParameterInfoImpl[] createdParmsInfo = ((JavaChangeInfoImpl)changeInfo).getCreatedParmsInfoWithoutVarargs();
        for (ParameterInfoImpl info : createdParmsInfo) {
          PsiExpression newArg;
          if (toInsertDefaultValue) {
            newArg = createDefaultValue(changeInfo, factory, info, list, substitutor);
          }
          else {
            newArg = factory.createExpressionFromText(info.getName(), list);
          }
          if (newArg != null) JavaCodeStyleManager.getInstance(list.getProject()).shortenClassReferences(list.add(newArg));
        }
      }
      else {
        final PsiExpression[] args = list.getExpressions();
        final int nonVarargCount = getNonVarargCount(changeInfo, args);
        final int varargCount = args.length - nonVarargCount;
        if (varargCount<0) return;
        PsiExpression[] newVarargInitializers = null;

        final int newArgsLength;
        final int newNonVarargCount;
        final JavaParameterInfo[] newParms = changeInfo.getNewParameters();
        if (changeInfo.isArrayToVarargs()) {
          newNonVarargCount = newParms.length - 1;
          final JavaParameterInfo lastNewParm = newParms[newParms.length - 1];
          final PsiExpression arrayToConvert = args[lastNewParm.getOldIndex()];
          if (arrayToConvert instanceof PsiNewExpression) {
            final PsiNewExpression expression = (PsiNewExpression)arrayToConvert;
            final PsiArrayInitializerExpression arrayInitializer = expression.getArrayInitializer();
            if (arrayInitializer != null) {
              newVarargInitializers = arrayInitializer.getInitializers();
            }
          }
          newArgsLength = newVarargInitializers == null ? newParms.length : newNonVarargCount + newVarargInitializers.length;
        }
        else if (changeInfo.isRetainsVarargs()) {
          newNonVarargCount = newParms.length - 1;
          newArgsLength = newNonVarargCount + varargCount;
        }
        else if (changeInfo.isObtainsVarags()) {
          newNonVarargCount = newParms.length - 1;
          newArgsLength = newNonVarargCount;
        }
        else {
          newNonVarargCount = newParms.length;
          newArgsLength = newParms.length;
        }

        String[] oldVarargs = null;
        if (changeInfo.wasVararg() && !changeInfo.isRetainsVarargs()) {
          oldVarargs = new String[varargCount];
          for (int i = nonVarargCount; i < args.length; i++) {
            oldVarargs[i - nonVarargCount] = args[i].getText();
          }
        }

        final PsiExpression[] newArgs = new PsiExpression[newArgsLength];
        for (int i = 0; i < newNonVarargCount; i++) {
          if (newParms[i].getOldIndex() == nonVarargCount && oldVarargs != null) {
            PsiType type = newParms[i].createType(changeInfo.getMethod(), list.getManager());
            if (type instanceof PsiArrayType) {
              type = substitutor.substitute(type);
              type = TypeConversionUtil.erasure(type);
              String typeText = type.getCanonicalText();
              if (type instanceof PsiEllipsisType) {
                typeText = typeText.replace("...", "[]");
              }
              String text = "new " + typeText + "{" + StringUtil.join(oldVarargs, ",") + "}";
              newArgs[i] = factory.createExpressionFromText(text, changeInfo.getMethod());
              continue;
            }
          }
          newArgs[i] = createActualArgument(changeInfo, list, newParms[i], toInsertDefaultValue, args, substitutor);
        }
        if (changeInfo.isArrayToVarargs()) {
          if (newVarargInitializers == null) {
            newArgs[newNonVarargCount] =
              createActualArgument(changeInfo, list, newParms[newNonVarargCount], toInsertDefaultValue, args, substitutor);
          }
          else {
            System.arraycopy(newVarargInitializers, 0, newArgs, newNonVarargCount, newVarargInitializers.length);
          }
        }
        else {
          final int newVarargCount = newArgsLength - newNonVarargCount;
          LOG.assertTrue(newVarargCount == 0 || newVarargCount == varargCount);
          for (int i = newNonVarargCount; i < newArgsLength; i++){
            final int oldIndex = newParms[newNonVarargCount].getOldIndex();
            if (oldIndex >= 0 && oldIndex != nonVarargCount) {
              newArgs[i] = createActualArgument(changeInfo, list, newParms[newNonVarargCount], toInsertDefaultValue, args, substitutor);
            } else {
              System.arraycopy(args, nonVarargCount, newArgs, newNonVarargCount, newVarargCount);
              break;
            }
          }
        }
        ChangeSignatureUtil.synchronizeList(list, Arrays.asList(newArgs), ExpressionList.INSTANCE, changeInfo.toRemoveParm());
      }
    }
  }

  private static int getNonVarargCount(JavaChangeInfo changeInfo, PsiExpression[] args) {
    if (!changeInfo.wasVararg()) return args.length;
    return changeInfo.getOldParameterTypes().length - 1;
  }


  @Nullable
  private static PsiExpression createActualArgument(JavaChangeInfo changeInfo,
                                                    final PsiExpressionList list,
                                                    final JavaParameterInfo info,
                                                    final boolean toInsertDefaultValue,
                                                    final PsiExpression[] args,
                                                    PsiSubstitutor substitutor) throws IncorrectOperationException {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(list.getProject()).getElementFactory();
    final int index = info.getOldIndex();
    if (index >= 0 && index < args.length) {
      return args[index];
    }
    else {
      if (toInsertDefaultValue) {
        return createDefaultValue(changeInfo, factory, info, list, substitutor);
      }
      else {
        return factory.createExpressionFromText(info.getName(), list);
      }
    }
  }

  @Nullable
  private static PsiExpression createDefaultValue(JavaChangeInfo changeInfo,
                                                  final PsiElementFactory factory,
                                                  final JavaParameterInfo info,
                                                  final PsiExpressionList list, PsiSubstitutor substitutor)
    throws IncorrectOperationException {
    if (info.isUseAnySingleVariable()) {
      final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(list.getProject()).getResolveHelper();
      final PsiType type = info.getTypeWrapper().getType(changeInfo.getMethod(), list.getManager());
      final VariablesProcessor processor = new VariablesProcessor(false) {
        @Override
        protected boolean check(PsiVariable var, ResolveState state) {
          if (var instanceof PsiField && !resolveHelper.isAccessible((PsiField)var, list, null)) return false;
          if (var instanceof PsiLocalVariable && list.getTextRange().getStartOffset() <= var.getTextRange().getStartOffset()) return false;
          if (PsiTreeUtil.isAncestor(var, list, false)) return false;
          final PsiType varType = state.get(PsiSubstitutor.KEY).substitute(var.getType());
          return type.isAssignableFrom(varType);
        }

        @Override
        public boolean execute(@NotNull PsiElement pe, @NotNull ResolveState state) {
          super.execute(pe, state);
          return size() < 2;
        }
      };
      PsiScopesUtil.treeWalkUp(processor, list, null);
      if (processor.size() == 1) {
        final PsiVariable result = processor.getResult(0);
        return factory.createExpressionFromText(result.getName(), list);
      }
      if (processor.size() == 0) {
        final PsiClass parentClass = PsiTreeUtil.getParentOfType(list, PsiClass.class);
        if (parentClass != null) {
          PsiClass containingClass = parentClass;
          final Set<PsiClass> containingClasses = new HashSet<>();
          while (containingClass != null) {
            if (type.isAssignableFrom(factory.createType(containingClass, PsiSubstitutor.EMPTY))) {
              containingClasses.add(containingClass);
            }
            containingClass = PsiTreeUtil.getParentOfType(containingClass, PsiClass.class);
          }
          if (containingClasses.size() == 1) {
            return RefactoringChangeUtil.createThisExpression(parentClass.getManager(), containingClasses.contains(parentClass) ? null
                                                                                                                                : containingClasses
                                                                                          .iterator().next());
          }
        }
      }
    }
    final PsiCallExpression callExpression = PsiTreeUtil.getParentOfType(list, PsiCallExpression.class);
    final String defaultValue = info.getDefaultValue();
    return callExpression != null ? (PsiExpression)info.getActualValue(callExpression, substitutor)
                                  : !StringUtil.isEmpty(defaultValue) ? factory.createExpressionFromText(defaultValue, list) : null;
  }


  @Override
  public boolean processPrimaryMethod(ChangeInfo changeInfo) {
    if (!StdLanguages.JAVA.equals(changeInfo.getLanguage()) || !(changeInfo instanceof JavaChangeInfo)) return false;
    final PsiElement element = changeInfo.getMethod();
    LOG.assertTrue(element instanceof PsiMethod);
    if (!JavaLanguage.INSTANCE.equals(element.getLanguage())) return false;
    if (changeInfo.isGenerateDelegate()) {
      generateDelegate((JavaChangeInfo)changeInfo);
    }
    processPrimaryMethod((JavaChangeInfo)changeInfo, (PsiMethod)element, null, true);
    return true;
  }

  @Override
  public boolean shouldPreviewUsages(ChangeInfo changeInfo, UsageInfo[] usages) {
    return false;
  }

  @Override
  public boolean setupDefaultValues(ChangeInfo changeInfo, Ref<UsageInfo[]> refUsages, Project project) {
    if (!(changeInfo instanceof JavaChangeInfo)) return true;
    for (UsageInfo usageInfo : refUsages.get()) {
      if (usageInfo instanceof  MethodCallUsageInfo) {
        MethodCallUsageInfo methodCallUsageInfo = (MethodCallUsageInfo)usageInfo;
        if (methodCallUsageInfo.isToChangeArguments()){
          final PsiElement element = methodCallUsageInfo.getElement();
          if (element == null) continue;
          final PsiMethod caller = RefactoringUtil.getEnclosingMethod(element);
          final boolean needDefaultValue = needDefaultValue(changeInfo, caller);
          if (needDefaultValue && (caller == null || !MethodSignatureUtil.isSuperMethod(methodCallUsageInfo.getReferencedMethod(), caller))) {
            final ParameterInfo[] parameters = changeInfo.getNewParameters();
            for (ParameterInfo parameter : parameters) {
              final String defaultValue = parameter.getDefaultValue();
              if (defaultValue == null && parameter.getOldIndex() == -1) {
                ((ParameterInfoImpl)parameter).setDefaultValue("");
                if (!ApplicationManager.getApplication().isUnitTestMode()) {
                  final PsiType type = ((ParameterInfoImpl)parameter).getTypeWrapper().getType(element);
                  final DefaultValueChooser chooser =
                    new DefaultValueChooser(project, parameter.getName(), PsiTypesUtil.getDefaultValueOfType(type));
                  if (chooser.showAndGet()) {
                    if (chooser.feelLucky()) {
                      parameter.setUseAnySingleVariable(true);
                    }
                    else {
                      ((ParameterInfoImpl)parameter).setDefaultValue(chooser.getDefaultValue());
                    }
                  }
                  else {
                    return false;
                  }
                }
              }
            }
          }
        }
      }
    }
    return true;
  }

  @Override
  public void registerConflictResolvers(List<ResolveSnapshotProvider.ResolveSnapshot> snapshots,
                                        @NotNull ResolveSnapshotProvider resolveSnapshotProvider,
                                        UsageInfo[] usages, ChangeInfo changeInfo) {
    snapshots.add(resolveSnapshotProvider.createSnapshot(changeInfo.getMethod()));
    for (UsageInfo usage : usages) {
      if (usage instanceof OverriderUsageInfo) {
        snapshots.add(resolveSnapshotProvider.createSnapshot(((OverriderUsageInfo)usage).getOverridingMethod()));
      }
    }
  }

  private static boolean needDefaultValue(ChangeInfo changeInfo, @Nullable PsiMethod method) {
    if (!(changeInfo instanceof JavaChangeInfoImpl)) {
      return true;
    }
    if (method != null) {
      final Set<PsiMethod> parametersMethods = ((JavaChangeInfoImpl)changeInfo).propagateParametersMethods;
      if (parametersMethods.contains(method)) {
        return false;
      }
      for (PsiMethod superMethod : method.findDeepestSuperMethods()) {
        if (parametersMethods.contains(superMethod)) {
          return false;
        }
      }
    }
    return true;
  }

  public static void generateDelegate(JavaChangeInfo changeInfo) throws IncorrectOperationException {
    final PsiMethod delegate = generateDelegatePrototype(changeInfo);
    PsiClass targetClass = changeInfo.getMethod().getContainingClass();
    LOG.assertTrue(targetClass != null);
    targetClass.addBefore(delegate, changeInfo.getMethod());
  }

  public static PsiMethod generateDelegatePrototype(JavaChangeInfo changeInfo) {
    final PsiMethod delegate = (PsiMethod)changeInfo.getMethod().copy();
    PsiClass targetClass = changeInfo.getMethod().getContainingClass();
    LOG.assertTrue(targetClass != null);
    if (targetClass.isInterface() && delegate.getBody() == null) {
      delegate.getModifierList().setModifierProperty(PsiModifier.DEFAULT, true);
    }
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(targetClass.getProject());
    ChangeSignatureProcessor.makeEmptyBody(factory, delegate);
    final PsiCallExpression callExpression = ChangeSignatureProcessor.addDelegatingCallTemplate(delegate, changeInfo.getNewName());
    addDelegateArguments(changeInfo, factory, callExpression);
    return delegate;
  }


  private static void addDelegateArguments(JavaChangeInfo changeInfo, PsiElementFactory factory, final PsiCallExpression callExpression) throws IncorrectOperationException {
    final JavaParameterInfo[] newParms = changeInfo.getNewParameters();
    final String[] oldParameterNames = changeInfo.getOldParameterNames();
    for (JavaParameterInfo newParm : newParms) {
      final PsiExpression actualArg;
      if (newParm.getOldIndex() >= 0) {
        actualArg = factory.createExpressionFromText(oldParameterNames[newParm.getOldIndex()], callExpression);
      }
      else {
        actualArg = (PsiExpression)newParm.getActualValue(callExpression, PsiSubstitutor.EMPTY);
      }
      final PsiExpressionList argumentList = callExpression.getArgumentList();
      if (actualArg != null && argumentList != null) {
        JavaCodeStyleManager.getInstance(callExpression.getProject()).shortenClassReferences(argumentList.add(actualArg));
      }
    }
  }

  private static void processPrimaryMethod(JavaChangeInfo changeInfo, PsiMethod method,
                                           PsiMethod baseMethod,
                                           boolean isOriginal) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();

    if (changeInfo.isVisibilityChanged()) {
      PsiModifierList modifierList = method.getModifierList();
      final String highestVisibility = isOriginal
                                       ? changeInfo.getNewVisibility()
                                       : VisibilityUtil.getHighestVisibility(changeInfo.getNewVisibility(),
                                                                             VisibilityUtil.getVisibilityModifier(modifierList));
      VisibilityUtil.setVisibility(modifierList, highestVisibility);
    }

    if (changeInfo.isNameChanged()) {
      String newName = baseMethod == null ? changeInfo.getNewName() :
                       RefactoringUtil.suggestNewOverriderName(method.getName(), baseMethod.getName(), changeInfo.getNewName());

      if (newName != null && !newName.equals(method.getName())) {
        final PsiIdentifier nameId = method.getNameIdentifier();
        assert nameId != null : method;
        nameId.replace(JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createIdentifier(newName));
      }
    }

    final PsiSubstitutor substitutor =
      baseMethod == null ? PsiSubstitutor.EMPTY : ChangeSignatureProcessor.calculateSubstitutor(method, baseMethod);

    final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(method.getProject());
    if (changeInfo.isReturnTypeChanged()) {
      PsiType newTypeElement = changeInfo.getNewReturnType().getType(changeInfo.getMethod().getParameterList(), method.getManager());
      final PsiType returnType = substitutor.substitute(newTypeElement);
      // don't modify return type for non-Java overriders (EJB)
      if (method.getName().equals(changeInfo.getNewName())) {
        final PsiTypeElement typeElement = method.getReturnTypeElement();
        if (typeElement != null) {
          javaCodeStyleManager.shortenClassReferences(typeElement.replace(factory.createTypeElement(returnType)));
        }
      }
    }

    PsiParameterList list = method.getParameterList();
    int newParamsLength = processMethodParams(changeInfo, baseMethod, factory, substitutor, list, method.getBody());
    fixJavadocsForChangedMethod(method, changeInfo, newParamsLength);
    if (changeInfo.isExceptionSetOrOrderChanged()) {
      final PsiClassType[] newExceptions = getPrimaryChangedExceptionInfo(changeInfo);
      fixPrimaryThrowsLists(method, newExceptions);
    }

    if (baseMethod == null && method.findSuperMethods().length == 0) {
      final PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, true, Override.class.getName());
      if (annotation != null) {
        annotation.delete();
      }
    }
  }

  private static int processMethodParams(JavaChangeInfo changeInfo,
                                         PsiMethod baseMethod,
                                         PsiElementFactory factory,
                                         PsiSubstitutor substitutor,
                                         PsiParameterList list,
                                         PsiElement methodBody) {
    PsiParameter[] parameters = list.getParameters();

    final JavaParameterInfo[] parameterInfos = changeInfo.getNewParameters();
    final int delta = baseMethod != null ? baseMethod.getParameterList().getParametersCount() - list.getParametersCount() : 0;
    PsiParameter[] newParms = new PsiParameter[Math.max(parameterInfos.length - delta, 0)];
    final String[] oldParameterNames = changeInfo.getOldParameterNames();
    final String[] oldParameterTypes = changeInfo.getOldParameterTypes();
    for (int i = 0; i < newParms.length; i++) {
      JavaParameterInfo info = parameterInfos[i];
      int index = info.getOldIndex();
      if (index >= 0) {
        PsiParameter parameter = parameters[index];
        newParms[i] = parameter;

        String oldName = oldParameterNames[index];
        if (!oldName.equals(info.getName()) && oldName.equals(parameter.getName())) {
          PsiIdentifier newIdentifier = factory.createIdentifier(info.getName());
          parameter.getNameIdentifier().replace(newIdentifier);
        }

        PsiTypeElement typeElement = parameter.getTypeElement();
        if (typeElement != null) {
          parameter.normalizeDeclaration();
          typeElement = parameter.getTypeElement();
          String oldType = oldParameterTypes[index];
          if (!oldType.equals(info.getTypeText())) {
            PsiType newType =
              substitutor.substitute(info.createType(changeInfo.getMethod().getParameterList(), changeInfo.getMethod().getManager()));
            typeElement.replace(factory.createTypeElement(newType));
          }
        }
      }
      else {
        newParms[i] = createNewParameter(changeInfo, info, substitutor);
      }
    }


    resolveParameterVsFieldsConflicts(newParms, list, changeInfo.toRemoveParm(), methodBody);
    return newParms.length;
  }

  private static PsiClassType[] getPrimaryChangedExceptionInfo(JavaChangeInfo changeInfo) throws IncorrectOperationException {
    final ThrownExceptionInfo[] newExceptionInfos = changeInfo.getNewExceptions();
    PsiClassType[] newExceptions = new PsiClassType[newExceptionInfos.length];
    final PsiMethod method = changeInfo.getMethod();
    for (int i = 0; i < newExceptions.length; i++) {
      newExceptions[i] =
        (PsiClassType)newExceptionInfos[i].createType(method, method.getManager()); //context really does not matter here
    }
    return newExceptions;
  }


  private static void processCallerMethod(JavaChangeInfo changeInfo, PsiMethod caller,
                                          PsiMethod baseMethod,
                                          boolean toInsertParams,
                                          boolean toInsertThrows) throws IncorrectOperationException {
    LOG.assertTrue(toInsertParams || toInsertThrows);
    if (toInsertParams) {
      List<PsiParameter> newParameters = new ArrayList<>();
      ContainerUtil.addAll(newParameters, caller.getParameterList().getParameters());
      final JavaParameterInfo[] primaryNewParms = changeInfo.getNewParameters();
      PsiSubstitutor substitutor =
        baseMethod == null ? PsiSubstitutor.EMPTY : ChangeSignatureProcessor.calculateSubstitutor(caller, baseMethod);
      final PsiClass aClass = changeInfo.getMethod().getContainingClass();
      final PsiClass callerContainingClass = caller.getContainingClass();
      final PsiSubstitutor psiSubstitutor = aClass != null && callerContainingClass != null && callerContainingClass.isInheritor(aClass, true)
                                            ? TypeConversionUtil.getSuperClassSubstitutor(aClass, callerContainingClass, substitutor)
                                            : PsiSubstitutor.EMPTY;
      for (JavaParameterInfo info : primaryNewParms) {
        if (info.getOldIndex() < 0) newParameters.add(createNewParameter(changeInfo, info, psiSubstitutor, substitutor));
      }
      PsiParameter[] arrayed = newParameters.toArray(new PsiParameter[newParameters.size()]);
      boolean[] toRemoveParm = new boolean[arrayed.length];
      Arrays.fill(toRemoveParm, false);
      resolveParameterVsFieldsConflicts(arrayed, caller.getParameterList(), toRemoveParm, caller.getBody());
    }

    if (toInsertThrows) {
      List<PsiJavaCodeReferenceElement> newThrowns = new ArrayList<>();
      final PsiReferenceList throwsList = caller.getThrowsList();
      ContainerUtil.addAll(newThrowns, throwsList.getReferenceElements());
      final ThrownExceptionInfo[] primaryNewExns = changeInfo.getNewExceptions();
      for (ThrownExceptionInfo thrownExceptionInfo : primaryNewExns) {
        if (thrownExceptionInfo.getOldIndex() < 0) {
          final PsiClassType type = (PsiClassType)thrownExceptionInfo.createType(caller, caller.getManager());
          final PsiJavaCodeReferenceElement ref =
            JavaPsiFacade.getInstance(caller.getProject()).getElementFactory().createReferenceElementByType(type);
          newThrowns.add(ref);
        }
      }
      PsiJavaCodeReferenceElement[] arrayed = newThrowns.toArray(new PsiJavaCodeReferenceElement[newThrowns.size()]);
      boolean[] toRemoveParm = new boolean[arrayed.length];
      Arrays.fill(toRemoveParm, false);
      ChangeSignatureUtil.synchronizeList(throwsList, Arrays.asList(arrayed), ThrowsList.INSTANCE, toRemoveParm);
    }
  }

  private static void fixPrimaryThrowsLists(PsiMethod method, PsiClassType[] newExceptions) throws IncorrectOperationException {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
    PsiJavaCodeReferenceElement[] refs = new PsiJavaCodeReferenceElement[newExceptions.length];
    for (int i = 0; i < refs.length; i++) {
      refs[i] = elementFactory.createReferenceElementByType(newExceptions[i]);
    }
    PsiReferenceList throwsList = elementFactory.createReferenceList(refs);

    PsiReferenceList methodThrowsList = (PsiReferenceList)method.getThrowsList().replace(throwsList);
    methodThrowsList = (PsiReferenceList)JavaCodeStyleManager.getInstance(method.getProject()).shortenClassReferences(methodThrowsList);
    CodeStyleManager.getInstance(method.getManager().getProject())
        .reformatRange(method, method.getParameterList().getTextRange().getEndOffset(),
                       methodThrowsList.getTextRange().getEndOffset());
  }

  private static void fixJavadocsForChangedMethod(final PsiMethod method, final JavaChangeInfo changeInfo, int newParamsLength) throws IncorrectOperationException {
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final JavaParameterInfo[] newParms = changeInfo.getNewParameters();
    LOG.assertTrue(parameters.length <= newParamsLength);
    final Set<PsiParameter> newParameters = new HashSet<>();
    final String[] oldParameterNames = changeInfo.getOldParameterNames();
    for (int i = 0; i < newParamsLength; i++) {
      JavaParameterInfo newParm = newParms[i];
      if (newParm.getOldIndex() < 0 ||
          newParm.getOldIndex() == i && !(newParm.getName().equals(oldParameterNames[newParm.getOldIndex()]) && newParm.getTypeText().equals(changeInfo.getOldParameterTypes()[newParm.getOldIndex()]))) {
        newParameters.add(parameters[i]);
      }
    }
    RefactoringUtil.fixJavadocsForParams(method, newParameters, pair -> {
      final PsiParameter parameter = pair.first;
      final String oldParamName = pair.second;
      final int oldIdx = ArrayUtil.find(oldParameterNames, oldParamName);
      int newIndex = method.getParameterList().getParameterIndex(parameter);
      return oldIdx >= 0 && newIndex >= 0 && changeInfo.getNewParameters()[newIndex].getOldIndex() == oldIdx;
    }, paramName -> ArrayUtil.find(oldParameterNames, paramName) >= 0);
  }

  private static PsiParameter createNewParameter(JavaChangeInfo changeInfo, JavaParameterInfo newParm,
                                                 PsiSubstitutor... substitutor) throws IncorrectOperationException {
    final PsiParameterList list = changeInfo.getMethod().getParameterList();
    final Project project = list.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    PsiType type = newParm.createType(list);
    for (PsiSubstitutor psiSubstitutor : substitutor) {
      type = psiSubstitutor.substitute(type);
    }
    PsiParameter parameter = factory.createParameter(newParm.getName(), type, list);
    if (CodeStyleSettingsManager.getSettings(project).getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_PARAMETERS) {
      PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL, true);
    }
    return parameter;
  }

  private static void resolveParameterVsFieldsConflicts(final PsiParameter[] newParms,
                                                        final PsiParameterList list,
                                                        boolean[] toRemoveParm, 
                                                        final PsiElement methodBody) throws IncorrectOperationException {
    List<FieldConflictsResolver> conflictResolvers = new ArrayList<>();
    for (PsiParameter parameter : newParms) {
      conflictResolvers.add(new FieldConflictsResolver(parameter.getName(), methodBody));
    }
    ChangeSignatureUtil.synchronizeList(list, Arrays.asList(newParms), ParameterList.INSTANCE, toRemoveParm);
    JavaCodeStyleManager.getInstance(list.getProject()).shortenClassReferences(list);
    for (FieldConflictsResolver fieldConflictsResolver : conflictResolvers) {
      fieldConflictsResolver.fix();
    }
  }

  private static boolean needToCatchExceptions(JavaChangeInfo changeInfo, PsiMethod caller) {
    return changeInfo.isExceptionSetOrOrderChanged() &&
           !(changeInfo instanceof JavaChangeInfoImpl && ((JavaChangeInfoImpl)changeInfo).propagateExceptionsMethods.contains(caller));
  }

  private static class ParameterList implements ChangeSignatureUtil.ChildrenGenerator<PsiParameterList, PsiParameter> {
    public static final ParameterList INSTANCE = new ParameterList();

    @Override
    public List<PsiParameter> getChildren(PsiParameterList psiParameterList) {
      return Arrays.asList(psiParameterList.getParameters());
    }
  }

  private static class ThrowsList implements ChangeSignatureUtil.ChildrenGenerator<PsiReferenceList, PsiJavaCodeReferenceElement> {
    public static final ThrowsList INSTANCE = new ThrowsList();

    @Override
    public List<PsiJavaCodeReferenceElement> getChildren(PsiReferenceList throwsList) {
      return Arrays.asList(throwsList.getReferenceElements());
    }
  }

  public static class ConflictSearcher {
    private final JavaChangeInfo myChangeInfo;

    private ConflictSearcher(@NotNull JavaChangeInfo changeInfo) {
      this.myChangeInfo = changeInfo;
    }

    public MultiMap<PsiElement, String> findConflicts(Ref<UsageInfo[]> refUsages) {
      MultiMap<PsiElement, String> conflictDescriptions = new MultiMap<>();
      final PsiMethod prototype = addMethodConflicts(conflictDescriptions);
      Set<UsageInfo> usagesSet = new HashSet<>(Arrays.asList(refUsages.get()));
      RenameUtil.removeConflictUsages(usagesSet);
      if (myChangeInfo.isVisibilityChanged()) {
        try {
          addInaccessibilityDescriptions(usagesSet, conflictDescriptions);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      final boolean[] toRemove = myChangeInfo.toRemoveParm();
      //introduce parameter object deletes parameters but replaces their usages with generated code
      final boolean checkUnusedParameter = myChangeInfo.checkUnusedParameter();
      if (checkUnusedParameter) {
        checkParametersToDelete(myChangeInfo.getMethod(), toRemove, conflictDescriptions);
      }
      checkContract(conflictDescriptions, myChangeInfo.getMethod());

      for (UsageInfo usageInfo : usagesSet) {
        final PsiElement element = usageInfo.getElement();
        if (usageInfo instanceof OverriderUsageInfo) {
          final PsiMethod method = ((OverriderUsageInfo)usageInfo).getOverridingMethod();
          final PsiMethod baseMethod = ((OverriderUsageInfo)usageInfo).getBaseMethod();
          final int delta = baseMethod.getParameterList().getParametersCount() - method.getParameterList().getParametersCount();
          if (delta > 0) {
            if (toRemove.length > 0 && toRemove[toRemove.length - 1]) { //todo check if implicit parameter is not the last one
              conflictDescriptions.putValue(baseMethod, "Implicit last parameter should not be deleted");
            }
          }
          else if (prototype != null && baseMethod == myChangeInfo.getMethod()) {
            ConflictsUtil.checkMethodConflicts(method.getContainingClass(), method, prototype, conflictDescriptions);
            if (checkUnusedParameter) {
              checkParametersToDelete(method, toRemove, conflictDescriptions);
            }
          }

          checkContract(conflictDescriptions, method);
        }
        else if (element instanceof PsiMethodReferenceExpression && MethodReferenceUsageInfo.needToExpand(myChangeInfo)) {
          conflictDescriptions.putValue(element, RefactoringBundle.message("expand.method.reference.warning"));
        }
        else if (element instanceof PsiJavaCodeReferenceElement) {
          final PsiElement parent = element.getParent();
          if (parent instanceof PsiCallExpression) {
            final PsiExpressionList argumentList = ((PsiCallExpression)parent).getArgumentList();
            if (argumentList != null) {
              final PsiExpression[] args = argumentList.getExpressions();
              for (int i = 0; i < toRemove.length; i++) {
                if (toRemove[i] && i < args.length) {
                  if (RemoveUnusedVariableUtil.checkSideEffects(args[i], null, new ArrayList<>())) {
                    conflictDescriptions.putValue(args[i], "Parameter '" + myChangeInfo.getOldParameterNames()[i] + "' has usage that is not safe to delete");
                  }
                }
              }
            }
          }
        }
      }

      return conflictDescriptions;
    }

    private static void checkParametersToDelete(PsiMethod method, boolean[] toRemove, MultiMap<PsiElement, String> conflictDescriptions) {
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      final PsiCodeBlock body = method.getBody();
      if (body != null) {
        final LocalSearchScope searchScope = new LocalSearchScope(body);
        for (int i = 0; i < toRemove.length; i++) {
          if (toRemove[i] && ReferencesSearch.search(parameters[i], searchScope).findFirst() != null) {
            conflictDescriptions.putValue(parameters[i], StringUtil.capitalize(RefactoringUIUtil.getDescription(parameters[i], true)) + " is used in method body");
          }
        }
      }
    }

    private static void checkContract(MultiMap<PsiElement, String> conflictDescriptions, PsiMethod method) {
      PsiAnnotation contract = ControlFlowAnalyzer.findContractAnnotation(method);
      if (contract != null && !AnnotationUtil.isInferredAnnotation(contract)) {
        conflictDescriptions.putValue(method, "@Contract annotation will have to be changed manually");
      }
    }

    private boolean needToChangeCalls() {
      return myChangeInfo.isNameChanged() || myChangeInfo.isParameterSetOrOrderChanged() || myChangeInfo.isExceptionSetOrOrderChanged();
    }


    private void addInaccessibilityDescriptions(Set<UsageInfo> usages, MultiMap<PsiElement, String> conflictDescriptions)
      throws IncorrectOperationException {
      PsiMethod method = myChangeInfo.getMethod();
      PsiModifierList modifierList = (PsiModifierList)method.getModifierList().copy();
      String visibility = myChangeInfo.getNewVisibility();
      VisibilityUtil.setVisibility(modifierList, visibility);

      searchForHierarchyConflicts(method, conflictDescriptions, visibility);

      for (Iterator<UsageInfo> iterator = usages.iterator(); iterator.hasNext();) {
        UsageInfo usageInfo = iterator.next();
        PsiElement element = usageInfo.getElement();
        if (element != null) {
          if (element instanceof PsiQualifiedReference) {
            PsiClass accessObjectClass = null;
            PsiElement qualifier = ((PsiQualifiedReference)element).getQualifier();
            if (qualifier instanceof PsiExpression) {
              accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass((PsiExpression)qualifier).getElement();
            }

            if (!JavaPsiFacade.getInstance(element.getProject()).getResolveHelper()
              .isAccessible(method, modifierList, element, accessObjectClass, null)) {
              String message =
                RefactoringBundle.message("0.with.1.visibility.is.not.accessible.from.2",
                                          RefactoringUIUtil.getDescription(method, true),
                                          VisibilityUtil.toPresentableText(visibility),
                                          RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(element), true));
              conflictDescriptions.putValue(method, message);
              if (!needToChangeCalls()) {
                iterator.remove();
              }
            }
          }
        }
      }
    }

    public static void searchForHierarchyConflicts(PsiMethod method, MultiMap<PsiElement, String> conflicts, final String modifier) {
      SuperMethodsSearch.search(method, method.getContainingClass(), true, false).forEach(
        methodSignature -> {
          final PsiMethod superMethod = methodSignature.getMethod();
          if (!hasCompatibleVisibility(superMethod, true, modifier)) {
            conflicts.putValue(superMethod, IntentionPowerPackBundle.message(
              "0.will.have.incompatible.access.privileges.with.super.1",
              RefactoringUIUtil.getDescription(method, false),
              RefactoringUIUtil.getDescription(superMethod, true)));
          }
          return true;
        });
      OverridingMethodsSearch.search(method).forEach(overridingMethod -> {
        if (!isVisibleFromOverridingMethod(method, overridingMethod, modifier)) {
          conflicts.putValue(overridingMethod, IntentionPowerPackBundle.message(
            "0.will.no.longer.be.visible.from.overriding.1",
            RefactoringUIUtil.getDescription(method, false),
            RefactoringUIUtil.getDescription(overridingMethod, true)));
        }
        else if (!hasCompatibleVisibility(overridingMethod, false, modifier)) {
          conflicts.putValue(overridingMethod, IntentionPowerPackBundle.message(
            "0.will.have.incompatible.access.privileges.with.overriding.1",
            RefactoringUIUtil.getDescription(method, false),
            RefactoringUIUtil.getDescription(overridingMethod, true)));
        }
        return false;
      });
    }

    private static boolean hasCompatibleVisibility(PsiMethod method, boolean isSuper, final String modifier) {
      if (modifier.equals(PsiModifier.PRIVATE)) {
        return false;
      }
      else if (modifier.equals(PsiModifier.PACKAGE_LOCAL)) {
        if (isSuper) {
          return !(method.hasModifierProperty(PsiModifier.PUBLIC) || method.hasModifierProperty(PsiModifier.PROTECTED));
        }
        return true;
      }
      else if (modifier.equals(PsiModifier.PROTECTED)) {
        if (isSuper) {
          return !method.hasModifierProperty(PsiModifier.PUBLIC);
        }
        else {
          return method.hasModifierProperty(PsiModifier.PROTECTED) || method.hasModifierProperty(PsiModifier.PUBLIC);
        }
      }
      else if (modifier.equals(PsiModifier.PUBLIC)) {
        if (!isSuper) {
          return method.hasModifierProperty(PsiModifier.PUBLIC);
        }
        return true;
      }
      throw new AssertionError();
    }

    private static boolean isVisibleFromOverridingMethod(PsiMethod method, PsiMethod overridingMethod, final String modifier) {
      final PsiModifierList modifierListCopy = (PsiModifierList)method.getModifierList().copy();
      modifierListCopy.setModifierProperty(modifier, true);
      return JavaResolveUtil.isAccessible(method, method.getContainingClass(), modifierListCopy, overridingMethod, null, null);
    }


    private PsiMethod addMethodConflicts(MultiMap<PsiElement, String> conflicts) {
      String newMethodName = myChangeInfo.getNewName();
      try {
        final PsiMethod method = myChangeInfo.getMethod();
        if (!StdLanguages.JAVA.equals(method.getLanguage())) return null;
        PsiManager manager = method.getManager();
        PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        final CanonicalTypes.Type returnType = myChangeInfo.getNewReturnType();
        PsiMethod prototype;
        if (returnType != null) {
          prototype = factory.createMethod(newMethodName, returnType.getType(method, manager));
        }
        else {
          prototype = factory.createConstructor();
          prototype.setName(newMethodName);
        }
        JavaParameterInfo[] parameters = myChangeInfo.getNewParameters();


        for (JavaParameterInfo info : parameters) {
          PsiType parameterType = info.createType(method, manager);
          if (parameterType == null) {
            parameterType =
              JavaPsiFacade.getElementFactory(method.getProject()).createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, method);
          }
          PsiParameter param = factory.createParameter(info.getName(), parameterType, method);
          if (CodeStyleSettingsManager.getSettings(manager.getProject())
            .getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_PARAMETERS) {
            PsiUtil.setModifierProperty(param, PsiModifier.FINAL, true);
          }
          prototype.getParameterList().add(param);
        }

        ConflictsUtil.checkMethodConflicts(method.getContainingClass(), myChangeInfo.isGenerateDelegate() ? null : method, prototype, conflicts);
        return prototype;
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
      return null;
    }
  }

  private static class ExpressionList implements ChangeSignatureUtil.ChildrenGenerator<PsiExpressionList, PsiExpression> {
    public static final ExpressionList INSTANCE = new ExpressionList();

    @Override
    public List<PsiExpression> getChildren(PsiExpressionList psiExpressionList) {
      return Arrays.asList(psiExpressionList.getExpressions());
    }
  }

}
