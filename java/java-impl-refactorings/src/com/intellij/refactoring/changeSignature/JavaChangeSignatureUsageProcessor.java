// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableUtil;
import com.intellij.codeInsight.generation.surroundWith.SurroundWithUtil;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
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
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.refactoring.util.*;
import com.intellij.refactoring.util.usageInfo.DefaultConstructorImplicitUsageInfo;
import com.intellij.refactoring.util.usageInfo.NoConstructorClassUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

/**
 * @author Maxim.Medvedev
 */
public final class JavaChangeSignatureUsageProcessor implements ChangeSignatureUsageProcessor {
  private static final Logger LOG = Logger.getInstance(JavaChangeSignatureUsageProcessor.class);

  private static boolean isJavaUsage(UsageInfo info) {
    final PsiElement element = info.getElement();
    if (element == null) return false;
    return element.getLanguage() == JavaLanguage.INSTANCE;
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
  public MultiMap<PsiElement, @DialogMessage String> findConflicts(ChangeInfo info, Ref<UsageInfo[]> refUsages) {
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
    JavaChangeInfo javaChangeInfo = JavaChangeInfoConverters.getJavaChangeInfo(changeInfo, usage);
    if (javaChangeInfo == null) {
      return false;
    }

    if (beforeMethodChange) {
      if (usage instanceof final CallerUsageInfo callerUsageInfo) {
        processCallerMethod(javaChangeInfo, callerUsageInfo.getMethod(), null, callerUsageInfo.isToInsertParameter(),
                            callerUsageInfo.isToInsertException());
        return true;
      }
      else if (usage instanceof final OverriderUsageInfo info) {
        final PsiMethod method = info.getOverridingMethod();
        final PsiMethod baseMethod = info.getBaseMethod();
        if (info.isOriginalOverrider()) {
          processPrimaryMethod(javaChangeInfo, method, baseMethod, false);
        }
        else {
          processCallerMethod(javaChangeInfo, method, baseMethod, info.isToInsertArgs(), info.isToCatchExceptions());
        }
        return true;
      }
      else if (usage instanceof final DeconstructionUsageInfo info) {
        final PsiDeconstructionPattern deconstruction = info.getDeconstruction();
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(usage.getProject());
        var processor = new DeconstructionProcessor(javaChangeInfo, elementFactory, deconstruction.getDeconstructionList());
        processor.run();
        return true;
      }
      else if (usage instanceof MethodReferenceUsageInfo && MethodReferenceUsageInfo.needToExpand(javaChangeInfo)) {
        final PsiElement element = usage.getElement();
        if (element instanceof PsiMethodReferenceExpression) {
          final PsiExpression expression = LambdaRefactoringUtil.convertToMethodCallInLambdaBody((PsiMethodReferenceExpression)element);
          if (expression instanceof PsiCallExpression) {
            ((MethodReferenceUsageInfo)usage).setCallExpression((PsiCallExpression)expression);
            return true;
          }
        }
      }
      else if (usage instanceof FunctionalInterfaceChangedUsageInfo functionalUsage) {
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(usage.getProject());
        final PsiElement element = usage.getElement();
        final PsiMethod interfaceMethod = functionalUsage.getMethod();
        if (element instanceof PsiLambdaExpression lambda) {
          MethodParamsProcessor processor =
            new MethodParamsProcessor(javaChangeInfo, interfaceMethod, elementFactory, PsiSubstitutor.EMPTY, lambda.getParameterList(),
                                      lambda.getBody());
          processor.run();
        }
        else if (element instanceof PsiMethodReferenceExpression methodRef) {
          final PsiLambdaExpression lambdaExpression =
            LambdaRefactoringUtil.convertMethodReferenceToLambda(methodRef, false, true);
          if (lambdaExpression != null) {
            MethodParamsProcessor processor =
              new MethodParamsProcessor(javaChangeInfo, interfaceMethod, elementFactory, PsiSubstitutor.EMPTY, lambdaExpression.getParameterList(),
                                        lambdaExpression.getBody());
            processor.run();
          }
        }
        return true;
      }

    }
    else {
      PsiElement element = usage.getElement();
      LOG.assertTrue(element != null);

      if (usage instanceof final DefaultConstructorImplicitUsageInfo defConstructorUsage) {
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
        addSuperCall(javaChangeInfo, constructor, defConstructorUsage.getBaseConstructor(), usages);
        return true;
      }
      else if (usage instanceof NoConstructorClassUsageInfo) {
        addDefaultConstructor(javaChangeInfo, ((NoConstructorClassUsageInfo)usage).getPsiClass(), usages);
        return true;
      }
      else if (usage instanceof MethodReferenceUsageInfo && MethodReferenceUsageInfo.needToExpand(javaChangeInfo)) {
        final MethodCallUsageInfo methodCallInfo = ((MethodReferenceUsageInfo)usage).createMethodCallInfo();
        if (methodCallInfo != null) {
          processMethodUsage(methodCallInfo.getElement(), javaChangeInfo, methodCallInfo.isToChangeArguments(),
                             methodCallInfo.isToCatchExceptions(), methodCallInfo.isVarArgCall(), 
                             methodCallInfo.getReferencedMethod(), methodCallInfo.getSubstitutor(), usages);
          return true;
        }
      }
      else if (usage instanceof final MethodCallUsageInfo methodCallInfo) {
        processMethodUsage(methodCallInfo.getElement(), javaChangeInfo, methodCallInfo.isToChangeArguments(),
                           methodCallInfo.isToCatchExceptions(), methodCallInfo.isVarArgCall(), 
                           methodCallInfo.getReferencedMethod(), methodCallInfo.getSubstitutor(), usages);
        return true;
      }
      else if (usage instanceof ChangeSignatureParameterUsageInfo) {
        String newName = ((ChangeSignatureParameterUsageInfo)usage).newParameterName;
        String oldName = ((ChangeSignatureParameterUsageInfo)usage).oldParameterName;
        if (element instanceof PsiReferenceExpression) {
          processParameterUsage((PsiReferenceExpression)element, oldName, newName);
        }
        return true;
      }
      else if (usage instanceof RecordGetterDeclarationUsageInfo) {
        processRecordGetter((PsiMethod)element, ((RecordGetterDeclarationUsageInfo)usage).myNewName,
                            ((RecordGetterDeclarationUsageInfo)usage).myNewType);
      }
      else if (usage instanceof CallReferenceUsageInfo callRefUsage) {
        callRefUsage.getReference().handleChangeSignature(changeInfo);
        return true;
      }
      else if (element instanceof PsiEnumConstant enumConstant) {
        fixActualArgumentsList(enumConstant.getArgumentList(), javaChangeInfo, true, PsiSubstitutor.EMPTY, false);
        return true;
      }
      else if (!(usage instanceof OverriderUsageInfo) && !(usage instanceof UnresolvableCollisionUsageInfo)) {
        PsiReference reference = usage instanceof MoveRenameUsageInfo ? usage.getReference() : element.getReference();
        if (element instanceof PsiImportStaticReferenceElement) {
          ((PsiImportStaticReferenceElement)element).handleElementRename(javaChangeInfo.getNewName());
        } else if (reference != null) {
          reference.bindToElement(javaChangeInfo.getMethod());
        }
      }
    }
    return false;
  }

  private static void processRecordGetter(PsiMethod method, String newName, String type) {
    Project project = method.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    if (newName != null && !newName.equals(method.getName())) {
      final PsiIdentifier nameId = method.getNameIdentifier();
      assert nameId != null : method;
      nameId.replace(factory.createIdentifier(newName));
    }
    PsiTypeElement element = method.getReturnTypeElement();
    if (element != null && !element.getType().equalsToText(type)) {
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(element.replace(factory.createTypeElementFromText(type, method)));
    }
  }

  private static void processParameterUsage(PsiReferenceExpression ref, String oldName, String newName)
    throws IncorrectOperationException {

    PsiElement last = ref.getReferenceNameElement();
    if (last instanceof PsiIdentifier && last.getText().equals(oldName)) {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(ref.getProject());
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
        fixActualArgumentsList(argumentList, changeInfo, true, substitutor, false);
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
    processMethodUsage(callExpression.getMethodExpression(), changeInfo, 
                       true, false, changeInfo.wasVararg(), callee, substitutor, usages);
  }

  private static void processMethodUsage(PsiElement ref,
                                         JavaChangeInfo changeInfo,
                                         boolean toChangeArguments,
                                         boolean toCatchExceptions,
                                         boolean varArgCall, 
                                         PsiMethod callee, 
                                         PsiSubstitutor substitutor,
                                         final UsageInfo[] usages) throws IncorrectOperationException {
    if (changeInfo.isNameChanged()) {
      if (ref instanceof PsiJavaCodeReferenceElement) {
        PsiElement last = ((PsiJavaCodeReferenceElement)ref).getReferenceNameElement();
        if (last instanceof PsiIdentifier && last.getText().equals(changeInfo.getOldName())) {
          last.replace(changeInfo.getNewNameIdentifier());
        }
      }
    }

    final PsiMethod caller = PsiTreeUtil.getParentOfType(ref, PsiMethod.class);
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

      fixActualArgumentsList(list, changeInfo, toInsertDefaultValue, substitutor, varArgCall);
    }

    if (toCatchExceptions) {
      PsiStatement statement;
      if (!(ref instanceof PsiReferenceExpression &&
            (statement = PsiTreeUtil.getParentOfType(ref, PsiStatement.class)) != null &&
            JavaHighlightUtil.isSuperOrThisCall(statement, true, false))) {
        if (needToCatchExceptions(changeInfo, caller)) {
          PsiClassType[] newExceptions =
            callee != null ? getCalleeChangedExceptionInfo(callee) : getPrimaryChangedExceptionInfo(changeInfo);
          fixExceptions(ref, newExceptions);
        }
      }
    }
  }

  private static boolean callerSignatureIsAboutToChangeToo(final @NotNull PsiMethod caller, final UsageInfo[] usages) {
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
    if (context instanceof PsiTryStatement tryStatement) {
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
          PsiCodeBlock codeBlock = CommonJavaRefactoringUtil.expandExpressionLambdaToCodeBlock((PsiLambdaExpression)context);
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
    return result.toArray(PsiClassType.EMPTY_ARRAY);
  }

  private static void adjustPossibleEmptyTryStatement(PsiTryStatement tryStatement) throws IncorrectOperationException {
    PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (tryBlock != null) {
      if (tryStatement.getCatchSections().length == 0 &&
          tryStatement.getFinallyBlock() == null &&
          tryStatement.getResourceList() == null) {
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
        JavaPsiFacade.getElementFactory(tryStatement.getProject()).createCatchSection(type, name, tryStatement);
      tryStatement.add(catchSection);
    }
  }

  private static PsiClassType[] filterUnhandledExceptions(PsiClassType[] exceptions, PsiElement place) {
    List<PsiClassType> result = new ArrayList<>();
    for (PsiClassType exception : exceptions) {
      if (!ExceptionUtil.isHandled(exception, place)) result.add(exception);
    }
    return result.toArray(PsiClassType.EMPTY_ARRAY);
  }

  private static boolean isCatchParameterRedundant(PsiClassType catchParamType, Collection<? extends PsiClassType> thrownTypes) {
    for (PsiType exceptionType : thrownTypes) {
      if (exceptionType.isConvertibleFrom(catchParamType)) return false;
    }
    return true;
  }

  //This method works equally well for primary usages as well as for propagated callers' usages
  private static void fixActualArgumentsList(PsiExpressionList list,
                                             JavaChangeInfo changeInfo,
                                             boolean toInsertDefaultValue,
                                             PsiSubstitutor substitutor,
                                             boolean varArgCall) throws IncorrectOperationException {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(list.getProject());
    if (changeInfo.isParameterSetOrOrderChanged()) {
      if (changeInfo instanceof JavaChangeInfoImpl javaChangeInfo && javaChangeInfo.isPropagationEnabled) {
        final ParameterInfoImpl[] createdParamInfo = javaChangeInfo.getCreatedParmsInfoWithoutVarargs();
        for (ParameterInfoImpl info : createdParamInfo) {
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
        final int nonVarargCount = varArgCall ? getNonVarargCount(changeInfo, args) : args.length;
        final int varargCount = args.length - nonVarargCount;
        if (varargCount<0) return;
        PsiExpression[] newVarargInitializers = null;

        final int newArgsLength;
        final int newNonVarargCount;
        final JavaParameterInfo[] newParameters = changeInfo.getNewParameters();
        if (changeInfo.isArrayToVarargs()) {
          newNonVarargCount = newParameters.length - 1;
          final JavaParameterInfo lastNewParam = newParameters[newParameters.length - 1];
          final PsiExpression arrayToConvert = args[lastNewParam.getOldIndex()];
          if (arrayToConvert instanceof final PsiNewExpression expression) {
            final PsiArrayInitializerExpression arrayInitializer = expression.getArrayInitializer();
            if (arrayInitializer != null) {
              newVarargInitializers = arrayInitializer.getInitializers();
            }
          }
          newArgsLength = newVarargInitializers == null ? newParameters.length : newNonVarargCount + newVarargInitializers.length;
        }
        else if (changeInfo.isRetainsVarargs() && varArgCall) {
          newNonVarargCount = newParameters.length - 1;
          newArgsLength = newNonVarargCount + varargCount;
        }
        else if (changeInfo.isObtainsVarags()) {
          newNonVarargCount = newParameters.length - 1;
          newArgsLength = newNonVarargCount;
        }
        else {
          newNonVarargCount = newParameters.length;
          newArgsLength = newParameters.length;
        }

        String[] oldVarargs = null;
        if (varArgCall && changeInfo.wasVararg() && !changeInfo.isRetainsVarargs()) {
          oldVarargs = new String[varargCount];
          for (int i = nonVarargCount; i < args.length; i++) {
            oldVarargs[i - nonVarargCount] = args[i].getText();
          }
        }

        final PsiExpression[] newArgs = new PsiExpression[newArgsLength];
        for (int i = 0; i < newNonVarargCount; i++) {
          if (newParameters[i].getOldIndex() == nonVarargCount && oldVarargs != null) {
            PsiType type = newParameters[i].createType(changeInfo.getMethod(), list.getManager());
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
          newArgs[i] = createActualArgument(changeInfo, list, newParameters[i], toInsertDefaultValue, args, substitutor);
        }
        if (changeInfo.isArrayToVarargs()) {
          if (newVarargInitializers == null) {
            newArgs[newNonVarargCount] =
              createActualArgument(changeInfo, list, newParameters[newNonVarargCount], toInsertDefaultValue, args, substitutor);
          }
          else {
            System.arraycopy(newVarargInitializers, 0, newArgs, newNonVarargCount, newVarargInitializers.length);
          }
        }
        else {
          final int newVarargCount = newArgsLength - newNonVarargCount;
          LOG.assertTrue(newVarargCount == 0 || newVarargCount == varargCount);
          for (int i = newNonVarargCount; i < newArgsLength; i++){
            final int oldIndex = newParameters[newNonVarargCount].getOldIndex();
            if (oldIndex >= 0 && oldIndex != nonVarargCount) {
              newArgs[i] = createActualArgument(changeInfo, list, newParameters[newNonVarargCount], toInsertDefaultValue, args, substitutor);
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


  private static @Nullable PsiExpression createActualArgument(JavaChangeInfo changeInfo,
                                                              final PsiExpressionList list,
                                                              final JavaParameterInfo info,
                                                              final boolean toInsertDefaultValue,
                                                              final PsiExpression[] args,
                                                              PsiSubstitutor substitutor) throws IncorrectOperationException {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(list.getProject());
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

  private static @Nullable PsiExpression createDefaultValue(JavaChangeInfo changeInfo,
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
    if (!JavaLanguage.INSTANCE.equals(changeInfo.getLanguage()) || !(changeInfo instanceof JavaChangeInfo javaChangeInfo)) return false;
    final PsiMethod element = javaChangeInfo.getMethod();
    if (!JavaLanguage.INSTANCE.equals(element.getLanguage())) {
      return false;
    }
    if (!(element.isPhysical() || element instanceof LightRecordMethod || element instanceof LightRecordCanonicalConstructor)) {
      return false;
    }
    if (changeInfo.isGenerateDelegate()) {
      generateDelegate(javaChangeInfo);
    }
    processPrimaryMethod(javaChangeInfo, element, null, true);
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
      if (usageInfo instanceof MethodCallUsageInfo methodCallUsageInfo) {
        if (methodCallUsageInfo.isToChangeArguments()){
          final PsiElement element = methodCallUsageInfo.getElement();
          if (element == null) continue;
          final PsiMethod caller = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
          final boolean needDefaultValue = needDefaultValue(changeInfo, caller);
          if (needDefaultValue && (caller == null || !MethodSignatureUtil.isSuperMethod(methodCallUsageInfo.getReferencedMethod(), caller))) {
            final ParameterInfo[] parameters = changeInfo.getNewParameters();
            for (ParameterInfo parameter : parameters) {
              final String defaultValue = parameter.getDefaultValue();
              if (defaultValue == null && parameter.isNew()) {
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
  public void registerConflictResolvers(List<? super ResolveSnapshotProvider.ResolveSnapshot> snapshots,
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
    final JavaParameterInfo[] newParameters = changeInfo.getNewParameters();
    final String[] oldParameterNames = changeInfo.getOldParameterNames();
    for (JavaParameterInfo newParameter : newParameters) {
      final PsiExpression actualArg;
      if (newParameter.getOldIndex() >= 0) {
        actualArg = factory.createExpressionFromText(oldParameterNames[newParameter.getOldIndex()], callExpression);
      }
      else {
        actualArg = (PsiExpression)newParameter.getActualValue(callExpression, PsiSubstitutor.EMPTY);
      }
      final PsiExpressionList argumentList = callExpression.getArgumentList();
      if (actualArg != null && argumentList != null) {
        JavaCodeStyleManager.getInstance(callExpression.getProject()).shortenClassReferences(argumentList.add(actualArg));
      }
    }
  }

  public static void processPrimaryMethod(JavaChangeInfo changeInfo, PsiMethod method,
                                          PsiMethod baseMethod,
                                          boolean isOriginal) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(method.getProject());

    if (changeInfo.isVisibilityChanged()) {
      PsiModifierList modifierList = method.getModifierList();
      final String targetVisibility;
      if (isOriginal || changeInfo instanceof JavaChangeInfoImpl && ((JavaChangeInfoImpl)changeInfo).isPropagateVisibility()) {
        targetVisibility = changeInfo.getNewVisibility();
      }
      else {
        targetVisibility =
          VisibilityUtil.getHighestVisibility(changeInfo.getNewVisibility(), VisibilityUtil.getVisibilityModifier(modifierList));
      }
      VisibilityUtil.setVisibility(modifierList, targetVisibility);
    }

    if (changeInfo.isNameChanged()) {
      String newName = baseMethod == null ? changeInfo.getNewName() :
                       RefactoringUtil.suggestNewOverriderName(method.getName(), baseMethod.getName(), changeInfo.getNewName());

      if (newName != null && !newName.equals(method.getName())) {
        final PsiIdentifier nameId = method.getNameIdentifier();
        assert nameId != null : method;
        nameId.replace(JavaPsiFacade.getElementFactory(method.getProject()).createIdentifier(newName));
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
          ensureNullabilityAnnotationsDoNotRepeat(typeElement, returnType);
          PsiTypeElement replacementType = factory.createTypeElement(returnType);
          javaCodeStyleManager.shortenClassReferences(typeElement.replace(replacementType));
          if (replacementType.getText().startsWith("@")) {
            // Annotation could be moved to modifier list during the replacement
            javaCodeStyleManager.shortenClassReferences(method.getModifierList());
          }
        }
      }
    }

    PsiParameterList list = method.getParameterList();
    PsiClass aClass = method.getContainingClass();
    boolean isRecordCanonicalConstructor = aClass != null && JavaPsiRecordUtil.isCanonicalConstructor(method);
    boolean shouldChangeParameters = !isRecordCanonicalConstructor || JavaPsiRecordUtil.isExplicitCanonicalConstructor(method);
    int newParamsLength = MethodParamsProcessor.getNewParametersCount(changeInfo, baseMethod, list);

    if (shouldChangeParameters) {
      MethodParamsProcessor processor = new MethodParamsProcessor(changeInfo, baseMethod, factory, substitutor, list, method.getBody());
      processor.run();
    }
    if (isRecordCanonicalConstructor) {
      PsiRecordHeader header = aClass.getRecordHeader();
      if (header == null) {
        header = factory.createRecordHeaderFromText("", aClass);
        header = (PsiRecordHeader)aClass.addAfter(header, aClass.getTypeParameterList());
      }
      RecordHeaderProcessor processor = new RecordHeaderProcessor(changeInfo, factory, header);
      processor.run();
      if (method instanceof SyntheticElement) {
        method = Objects.requireNonNull(JavaPsiRecordUtil.findCanonicalConstructor(aClass));
      }
    }
    fixJavadocsForChangedMethod(method, changeInfo, newParamsLength);
    if (changeInfo.isExceptionSetOrOrderChanged()) {
      final PsiClassType[] newExceptions = getPrimaryChangedExceptionInfo(changeInfo);
      fixPrimaryThrowsLists(method, newExceptions);
    }

    tryUpdateContracts(method, changeInfo);

    if (baseMethod == null && method.findSuperMethods().length == 0) {
      final PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, true, Override.class.getName());
      if (annotation != null) {
        annotation.delete();
      }
    }
  }

  abstract static class Processor<Parent extends PsiElement, Child extends ChildWrapper> {
    protected final @NotNull PsiElementFactory myFactory;
    protected final @NotNull Parent myParent;
    protected final @NotNull JavaChangeInfo myChangeInfo;
    private final String @NotNull [] myOldParameterNames;
    private final String @NotNull [] myOldParameterTypes;

    Processor(@NotNull JavaChangeInfo changeInfo, @NotNull PsiElementFactory factory, @NotNull Parent parent) {
      myFactory = factory;
      myParent = parent;
      myChangeInfo = changeInfo;
      myOldParameterNames = changeInfo.getOldParameterNames();
      myOldParameterTypes = changeInfo.getOldParameterTypes();
    }

    void run() {
      final List<Child> result = new ArrayList<>();
      final JavaParameterInfo[] parameterInfos = myChangeInfo.getNewParameters();
      int newParamsLength = getNewParametersCount();
      for (int i = 0; i < newParamsLength; i++) {
        JavaParameterInfo info = parameterInfos[i];
        if (info.isNew()) {
          processNew(info, result);
        }
        else {
          processOld(info, result);
        }
      }
      process(result);
    }

    int getNewParametersCount() {
      return myChangeInfo.getNewParameters().length;
    }

    private void processOld(JavaParameterInfo info, List<Child> result) {
      int oldIndex = info.getOldIndex();
      Child child = getChild(oldIndex);
      if (child == null) {
        return;
      }
      result.add(child);
      String oldName = myOldParameterNames[oldIndex];
      if (!oldName.equals(info.getName()) && oldName.equals(child.getName())) {
        PsiIdentifier newIdentifier = myFactory.createIdentifier(info.getName());
        Objects.requireNonNull(child.getNameIdentifier()).replace(newIdentifier);
      }
      PsiTypeElement typeElement = child.getTypeElement();
      if (typeElement != null) {
        child.normalizeDeclaration();
        typeElement = child.getTypeElement();
        String oldType = myOldParameterTypes[oldIndex];
        if (!oldType.equals(info.getTypeText())) {
          PsiType newType = info.createType(myChangeInfo.getMethod().getParameterList(), myChangeInfo.getMethod().getManager());
          ensureNullabilityAnnotationsDoNotRepeat(typeElement, newType);
          PsiSubstitutor mySubstitutor = getSubstitutor();
          if (mySubstitutor != null) {
            newType = mySubstitutor.substitute(newType);
          }
          if (newType != null) {
            typeElement.replace(myFactory.createTypeElement(newType));
          }
        }
      }
    }

    protected abstract void process(List<Child> newElements);

    abstract void processNew(JavaParameterInfo info, List<Child> result);

    abstract @Nullable Child getChild(int index);

    protected PsiSubstitutor getSubstitutor() {
      return null;
    }
  }

  private static void ensureNullabilityAnnotationsDoNotRepeat(@NotNull PsiTypeElement element, PsiType newType) {
    PsiElement parent = element.getParent();
    if (parent instanceof PsiModifierListOwner owner &&
        newType != null &&
        ContainerUtil.exists(newType.getAnnotations(), annotation -> NullableNotNullManager.isNullabilityAnnotation(annotation))) {
      Arrays.stream(owner.getAnnotations())
        .filter(NullableNotNullManager::isNullabilityAnnotation)
        .forEach(PsiElement::delete);
    }
  }

  static final class RecordHeaderProcessor extends Processor<PsiRecordHeader, VariableWrapper> {

    RecordHeaderProcessor(@NotNull JavaChangeInfo changeInfo, @NotNull PsiElementFactory factory, @NotNull PsiRecordHeader header) {
      super(changeInfo, factory, header);
    }

    @Override
    void processNew(JavaParameterInfo info, List<VariableWrapper> result) {
      PsiType newType = info.createType(myParent);
      if (newType != null) {
        String componentText = newType.getCanonicalText() + " " + info.getName();
        PsiRecordComponent[] dummyComponents = myFactory.createRecordHeaderFromText(componentText, myParent).getRecordComponents();
        if (dummyComponents.length != 1) {
          throw new IncorrectOperationException(componentText + " is not a valid component");
        }
        result.add(new VariableWrapper(dummyComponents[0]));
      }
    }

    @Override
    VariableWrapper getChild(int index) {
      return new VariableWrapper(myParent.getRecordComponents()[index]);
    }

    @Override
    protected void process(@NotNull List<VariableWrapper> newElements) {
      final List<PsiRecordComponent> newComponents = ContainerUtil.map(newElements, element -> (PsiRecordComponent)element.variable());
      ChangeSignatureUtil.synchronizeList(myParent, newComponents, RecordHeader.INSTANCE, myChangeInfo.toRemoveParm());
    }
  }

  static final class MethodParamsProcessor extends Processor<PsiParameterList, VariableWrapper> {
    private final @Nullable PsiElement myMethodBody;
    private final @NotNull PsiSubstitutor mySubstitutor;
    private final @Nullable PsiMethod myBaseMethod;

    MethodParamsProcessor(@NotNull JavaChangeInfo changeInfo,
                          @Nullable PsiMethod baseMethod,
                          @NotNull PsiElementFactory factory,
                          @NotNull PsiSubstitutor substitutor,
                          @NotNull PsiParameterList list,
                          @Nullable PsiElement methodBody) {
      super(changeInfo, factory, list);
      myMethodBody = methodBody;
      mySubstitutor = substitutor;
      myBaseMethod = baseMethod;
    }

    @Override
    void processNew(JavaParameterInfo info, List<VariableWrapper> result) {
      PsiElement parent = myParent.getParent();
      if (parent instanceof PsiLambdaExpression && !((PsiLambdaExpression)parent).hasFormalParameterTypes()) {
        PsiExpression dummyLambdaParam = myFactory.createExpressionFromText(info.getName() + "-> {}", myParent);
        result.add(new VariableWrapper(((PsiLambdaExpression)dummyLambdaParam).getParameterList().getParameters()[0]));
      }
      else {
        result.add(new VariableWrapper(createNewParameter(myChangeInfo, info, mySubstitutor)));
      }
    }

    @Override
    VariableWrapper getChild(int index) {
      return new VariableWrapper(Objects.requireNonNull(myParent.getParameter(index)));
    }

    @Override
    int getNewParametersCount() {
      return getNewParametersCount(myChangeInfo, myBaseMethod, myParent);
    }

    private static int getNewParametersCount(JavaChangeInfo changeInfo, PsiMethod baseMethod, PsiParameterList list) {
      final int delta = baseMethod != null ? baseMethod.getParameterList().getParametersCount() - list.getParametersCount() : 0;
      return Math.max(changeInfo.getNewParameters().length - delta, 0);
    }

    @Override
    protected @NotNull PsiSubstitutor getSubstitutor() {
      return mySubstitutor;
    }

    @Override
    protected void process(@NotNull List<VariableWrapper> newElements) {
      final List<PsiParameter> newParameters = ContainerUtil.map(newElements, element -> (PsiParameter)element.variable());
      final List<String> newParameterNames = ContainerUtil.map(newElements, VariableWrapper::getName);
      final boolean[] toRemove = myChangeInfo.toRemoveParm();
      if (myChangeInfo.isFixFieldConflicts()) {
        resolveVariableVsFieldsConflicts(newParameters, newParameterNames, myParent, toRemove, myMethodBody, ParameterList.INSTANCE);
      } else {
        ChangeSignatureUtil.synchronizeList(myParent, newParameters, ParameterList.INSTANCE, toRemove);
        JavaCodeStyleManager.getInstance(myParent.getProject()).shortenClassReferences(myParent);
      }
    }
  }

  static final class DeconstructionProcessor extends Processor<PsiDeconstructionList, PatternWrapper> {
    DeconstructionProcessor(@NotNull JavaChangeInfo changeInfo,
                            @NotNull PsiElementFactory factory,
                            @NotNull PsiDeconstructionList list) {
      super(changeInfo, factory, list);
    }

    @Override
    void processNew(JavaParameterInfo info, List<PatternWrapper> result) {
      PsiType newType = info.createType(myParent);
      if (newType != null) {
        String patternText = newType.getCanonicalText() + " " + info.getName();
        PsiExpression expression = myFactory.createExpressionFromText("x instanceof " + patternText, null);
        if (!(expression instanceof PsiInstanceOfExpression instanceOfExpression)) {
          throw new IncorrectOperationException(patternText + " is not a valid pattern");
        }
        PsiPattern pattern = instanceOfExpression.getPattern();
        result.add(new PatternWrapper(Objects.requireNonNull(pattern)));
      }
    }

    @Override
    PatternWrapper getChild(int index) {
      return new PatternWrapper(myParent.getDeconstructionComponents()[index]);
    }

    @Override
    protected void process(@NotNull List<PatternWrapper> elements) {
      final List<PsiPattern> newPatterns = ContainerUtil.map(elements, element -> element.pattern());
      final List<String> newElementNames = ContainerUtil.map(elements, PatternWrapper::getName);
      resolveVariableVsFieldsConflicts(newPatterns,
                                       newElementNames,
                                       myParent,
                                       myChangeInfo.toRemoveParm(),
                                       PsiTreeUtil.getParentOfType(myParent, PsiStatement.class),
                                       DeconstructionList.INSTANCE);
    }
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
      final JavaParameterInfo[] primaryNewParameters = changeInfo.getNewParameters();
      PsiSubstitutor substitutor =
        baseMethod == null ? PsiSubstitutor.EMPTY : ChangeSignatureProcessor.calculateSubstitutor(caller, baseMethod);
      final PsiClass aClass = changeInfo.getMethod().getContainingClass();
      final PsiClass callerContainingClass = caller.getContainingClass();
      final PsiSubstitutor psiSubstitutor = aClass != null && callerContainingClass != null && callerContainingClass.isInheritor(aClass, true)
                                            ? TypeConversionUtil.getSuperClassSubstitutor(aClass, callerContainingClass, substitutor)
                                            : PsiSubstitutor.EMPTY;
      for (JavaParameterInfo info : primaryNewParameters) {
        if (info.getOldIndex() < 0) newParameters.add(createNewParameter(changeInfo, info, psiSubstitutor, substitutor));
      }
      boolean[] toRemoveParameters = new boolean[newParameters.size()];
      Arrays.fill(toRemoveParameters, false);
      final List<String> newParameterNames = ContainerUtil.map(newParameters, parameter -> parameter.getName());
      resolveVariableVsFieldsConflicts(newParameters, newParameterNames, caller.getParameterList(), toRemoveParameters, caller.getBody(),
                                       ParameterList.INSTANCE);
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
            JavaPsiFacade.getElementFactory(caller.getProject()).createReferenceElementByType(type);
          newThrowns.add(ref);
        }
      }
      PsiJavaCodeReferenceElement[] arrayed = newThrowns.toArray(PsiJavaCodeReferenceElement.EMPTY_ARRAY);
      boolean[] toRemoveParameters = new boolean[arrayed.length];
      Arrays.fill(toRemoveParameters, false);
      ChangeSignatureUtil.synchronizeList(throwsList, Arrays.asList(arrayed), ThrowsList.INSTANCE, toRemoveParameters);
    }
  }

  private static void fixPrimaryThrowsLists(PsiMethod method, PsiClassType[] newExceptions) throws IncorrectOperationException {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(method.getProject());
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
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return;
    PsiDocComment methodDocComment =  method.getDocComment();
    PsiDocComment classDocComment = aClass.getDocComment();
    if (changeInfo.isParameterSetOrOrderChanged() || changeInfo.isParameterNamesChanged()) {
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
      Condition<Pair<PsiParameter, String>> eqCondition = pair -> {
        final PsiParameter parameter = pair.first;
        final String oldParamName = pair.second;
        final int oldIdx = ArrayUtil.find(oldParameterNames, oldParamName);
        int newIndex = method.getParameterList().getParameterIndex(parameter);
        return oldIdx >= 0 && newIndex >= 0 && changeInfo.getNewParameters()[newIndex].getOldIndex() == oldIdx;
      };
      Condition<String> matchedToOldParam = paramName -> ArrayUtil.find(oldParameterNames, paramName) >= 0;
      if (!(method instanceof SyntheticElement) && methodDocComment != null && methodDocComment.findTagByName("param") != null) {
        CommonJavaRefactoringUtil.fixJavadocsForParams(method, methodDocComment, newParameters, eqCondition, matchedToOldParam);
      }

      if (JavaPsiRecordUtil.isCanonicalConstructor(method) && classDocComment != null && classDocComment.findTagByName("param") != null) {
        CommonJavaRefactoringUtil.fixJavadocsForParams(method, classDocComment, newParameters, eqCondition, matchedToOldParam);
      }
    }

    if (changeInfo.isReturnTypeChanged() && methodDocComment != null) {
      CanonicalTypes.Type type = changeInfo.getNewReturnType();
      PsiDocTag aReturn = methodDocComment.findTagByName("return");
      PsiDocComment oldMethodDocComment = (PsiDocComment)methodDocComment.copy();
      if (PsiTypes.voidType().equalsToText(type.getTypeText())) {
        if (aReturn != null) {
          aReturn.delete();
        }
      }
      else {
        String oldReturnType = changeInfo.getOldReturnType();
        if (aReturn == null && oldReturnType != null && PsiTypes.voidType().equalsToText(oldReturnType)) {
          methodDocComment.add(JavaPsiFacade.getElementFactory(method.getProject()).createDocTagFromText("@return"));
        }
      }
      CommonJavaRefactoringUtil.formatJavadocIgnoringSettings(method, methodDocComment, oldMethodDocComment);
    }
  }

  private static PsiParameter createNewParameter(JavaChangeInfo changeInfo, JavaParameterInfo newParameters,
                                                 PsiSubstitutor... substitutor) throws IncorrectOperationException {
    PsiMethod method = changeInfo.getMethod();
    final PsiParameterList list = method.getParameterList();
    final Project project = list.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiType type = newParameters.createType(list);
    for (PsiSubstitutor psiSubstitutor : substitutor) {
      type = psiSubstitutor.substitute(type);
    }
    PsiParameter parameter = factory.createParameter(newParameters.getName(), type, list);
    if (JavaCodeStyleSettings.getInstance(ObjectUtils.notNull(method.getContext(), method).getContainingFile()).GENERATE_FINAL_PARAMETERS) {
      PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL, true);
    }
    return parameter;
  }

  private static <Parent extends PsiElement, Child extends PsiElement> void resolveVariableVsFieldsConflicts(
    final List<Child> newElements,
    final List<String> newElementNames,
    final Parent parent,
    boolean[] toRemoveParameters,
    final PsiElement methodBody,
    ChangeSignatureUtil.ChildrenGenerator<Parent, Child> generator) throws IncorrectOperationException
  {
    PsiUtilCore.ensureValid(parent);
    List<FieldConflictsResolver> conflictResolvers = new ArrayList<>();
    for (String newElementName : ContainerUtil.skipNulls(newElementNames)) {
      conflictResolvers.add(new FieldConflictsResolver(newElementName, methodBody));
    }
    ChangeSignatureUtil.synchronizeList(parent, newElements, generator, toRemoveParameters);
    LOG.assertTrue(parent.getContainingFile() != null, "No containing file for: " + parent.getClass());
    JavaCodeStyleManager.getInstance(parent.getProject()).shortenClassReferences(parent);
    for (FieldConflictsResolver fieldConflictsResolver : conflictResolvers) {
      fieldConflictsResolver.fix();
    }
  }

  private static boolean needToCatchExceptions(JavaChangeInfo changeInfo, PsiMethod caller) {
    return changeInfo.isExceptionSetOrOrderChanged() &&
           !(changeInfo instanceof JavaChangeInfoImpl && ((JavaChangeInfoImpl)changeInfo).propagateExceptionsMethods.contains(caller));
  }

  private static class DeconstructionList implements ChangeSignatureUtil.ChildrenGenerator<PsiDeconstructionList, PsiPattern> {
    public static final DeconstructionList INSTANCE = new DeconstructionList();

    @Override
    public List<PsiPattern> getChildren(PsiDeconstructionList psiParameterList) {
      return Arrays.asList(psiParameterList.getDeconstructionComponents());
    }
  }

  private static class ParameterList implements ChangeSignatureUtil.ChildrenGenerator<PsiParameterList, PsiParameter> {
    public static final ParameterList INSTANCE = new ParameterList();

    @Override
    public List<PsiParameter> getChildren(PsiParameterList psiParameterList) {
      return Arrays.asList(psiParameterList.getParameters());
    }
  }

  private static class RecordHeader implements ChangeSignatureUtil.ChildrenGenerator<PsiRecordHeader, PsiRecordComponent> {
    public static final RecordHeader INSTANCE = new RecordHeader();

    @Override
    public List<PsiRecordComponent> getChildren(PsiRecordHeader header) {
      return Arrays.asList(header.getRecordComponents());
    }
  }

  private static class ThrowsList implements ChangeSignatureUtil.ChildrenGenerator<PsiReferenceList, PsiJavaCodeReferenceElement> {
    public static final ThrowsList INSTANCE = new ThrowsList();

    @Override
    public List<PsiJavaCodeReferenceElement> getChildren(PsiReferenceList throwsList) {
      return Arrays.asList(throwsList.getReferenceElements());
    }
  }

  public static final class ConflictSearcher {
    private final JavaChangeInfo myChangeInfo;

    private ConflictSearcher(@NotNull JavaChangeInfo changeInfo) {
      this.myChangeInfo = changeInfo;
    }

    private MultiMap<PsiElement, @DialogMessage String> findConflicts(Ref<UsageInfo[]> refUsages) {
      MultiMap<PsiElement, @DialogMessage String> conflictDescriptions = new MultiMap<>();
      final PsiMethod prototype = addMethodConflicts(conflictDescriptions);
      Set<UsageInfo> usagesSet = ContainerUtil.newHashSet(refUsages.get());
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
      checkContract(conflictDescriptions, myChangeInfo.getMethod(), false);

      for (UsageInfo usageInfo : usagesSet) {
        final PsiElement element = usageInfo.getElement();
        if (usageInfo instanceof OverriderUsageInfo) {
          final PsiMethod method = ((OverriderUsageInfo)usageInfo).getOverridingMethod();
          final PsiMethod baseMethod = ((OverriderUsageInfo)usageInfo).getBaseMethod();
          final int delta = baseMethod.getParameterList().getParametersCount() - method.getParameterList().getParametersCount();
          if (delta > 0) {
            if (toRemove.length > 0 && toRemove[toRemove.length - 1]) { //todo check if implicit parameter is not the last one
              conflictDescriptions.putValue(baseMethod, JavaRefactoringBundle.message("implicit.last.parameter.warning"));
            }
          }
          else if (prototype != null && baseMethod == myChangeInfo.getMethod()) {
            ConflictsUtil.checkMethodConflicts(method.getContainingClass(), method, prototype, conflictDescriptions);
            if (checkUnusedParameter) {
              checkParametersToDelete(method, toRemove, conflictDescriptions);
            }
          }

          checkContract(conflictDescriptions, method, true);
        }
        else if (element instanceof PsiMethodReferenceExpression && MethodReferenceUsageInfo.needToExpand(myChangeInfo)) {
          conflictDescriptions.putValue(element, JavaRefactoringBundle.message("expand.method.reference.warning"));
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
                    final PsiParameter parameter = myChangeInfo.getMethod().getParameterList().getParameter(i);
                    if (parameter != null) {
                      String message =
                        JavaRefactoringBundle.message("safe.delete.parameter.usage.warning",
                                                      RefactoringUIUtil.getDescription(parameter, true));
                      conflictDescriptions.putValue(args[i], StringUtil.capitalize(message));
                    }
                  }
                }
              }
            }
          }
        }
      }

      return conflictDescriptions;
    }

    public static void checkParametersToDelete(PsiMethod method,
                                               boolean[] toRemove,
                                               MultiMap<PsiElement, @DialogMessage String> conflictDescriptions) {
      checkRecordComponentsToDelete(method, toRemove, conflictDescriptions);
      if (method instanceof SyntheticElement) return;
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      final PsiCodeBlock body = method.getBody();
      if (body != null) {
        final LocalSearchScope searchScope = new LocalSearchScope(body);
        final PsiMethodCallExpression superCall = getSuperCall(method, body);
        for (int i = 0; i < toRemove.length; i++) {
          if (toRemove[i]) {
            for (PsiReference ref : ReferencesSearch.search(parameters[i], searchScope).asIterable()) {
              if (superCall == null || !passUnchangedParameterToSuperCall(superCall, i, ref)) {
                String paramName = StringUtil.capitalize(RefactoringUIUtil.getDescription(parameters[i], true));
                conflictDescriptions.putValue(parameters[i], JavaRefactoringBundle.message("parameter.used.in.method.body.warning", paramName));
                break;
              }
            }
          }
        }
      }
    }

    private static void checkRecordComponentsToDelete(@NotNull PsiMethod method,
                                                      boolean[] toRemove,
                                                      @NotNull MultiMap<PsiElement, @DialogMessage String> conflictDescriptions) {
      PsiClass aClass = method.getContainingClass();
      if (aClass == null || !aClass.isRecord()) return;
      if (!JavaPsiRecordUtil.isCanonicalConstructor(method)) return;
      PsiRecordComponent @NotNull [] components = aClass.getRecordComponents();
      for (int i = 0; i < toRemove.length; i++) {
        if (!toRemove[i]) continue;
        if (components.length <= i) {
          break;
        }
        PsiRecordComponent component = components[i];
        //field
        PsiField field = JavaPsiRecordUtil.getFieldForComponent(component);
        if (field != null) {
          for (PsiReferenceExpression reference : VariableAccessUtils.getVariableReferences(field, aClass.getContainingFile())) {
            conflictDescriptions.putValue(reference, JavaRefactoringBundle.message("record.component.used.in.method.body.warning", component.getName()));
          }
        }
        //getter
        PsiMethod explicitGetter = JavaPsiRecordUtil.getAccessorForRecordComponent(component);
        if (explicitGetter != null) {
          for (PsiReference psiReference : ReferencesSearch.search(explicitGetter, explicitGetter.getUseScope(), false).asIterable()) {
            PsiElement paramRef = psiReference.getElement();
            conflictDescriptions.putValue(paramRef, JavaRefactoringBundle.message("record.component.used.in.method.body.warning", component.getName()));
          }
        }
      }
      //deconstruction. can be slow, because it is necessary to check a type of psiReference
      for (PsiReference classReference : ReferencesSearch.search(aClass, aClass.getUseScope()).asIterable()) {
        PsiElement element = classReference.getElement();
        PsiElement parent = element.getParent();
        if (!(parent instanceof PsiTypeElement)) {
          continue;
        }
        PsiElement grandParent = parent.getParent();
        if (grandParent instanceof PsiDeconstructionPattern deconstructionPattern) {
          PsiPattern[] deconstructionComponents = deconstructionPattern.getDeconstructionList().getDeconstructionComponents();
          if (deconstructionComponents.length != components.length) {
            continue;
          }
          for (int i = 0; i < toRemove.length; i++) {
            if (!toRemove[i]) continue;
            if (components.length <= i) {
              break;
            }
            PsiPattern deconstructionComponent = deconstructionComponents[i];
            PsiRecordComponent recordComponent = components[i];
            collectDeconstructionRecordToDeleteUsagesConflict(deconstructionComponent, aClass, conflictDescriptions, recordComponent);
          }
        }
      }
    }

    private static void collectDeconstructionRecordToDeleteUsagesConflict(@Nullable PsiPattern pattern,
                                                                          @NotNull PsiClass context,
                                                                          @NotNull MultiMap<PsiElement, String> conflictDescriptions,
                                                                          @NotNull PsiRecordComponent recordComponent) {
      if (pattern == null) return;
      if (!JavaPsiPatternUtil.isUnconditionalForType(pattern, recordComponent.getType(), true)) {
        conflictDescriptions.putValue(pattern, JavaRefactoringBundle.message("record.component.used.in.method.body.warning",
                                                                    recordComponent.getName()));
        return;
      }
      if (pattern instanceof PsiTypeTestPattern typeTestPattern) {
        PsiPatternVariable variable = typeTestPattern.getPatternVariable();
        if (variable == null || variable.isUnnamed()) return;
        if(VariableAccessUtils.variableIsUsed(variable, context.getContainingFile())) {
          conflictDescriptions.putValue(typeTestPattern, JavaRefactoringBundle.message("record.component.used.in.method.body.warning", recordComponent.getName()));
          return;
        }
      }
      if (pattern instanceof PsiDeconstructionPattern deconstructionPattern) {
        PsiType psiType = deconstructionPattern.getTypeElement().getType();
        PsiClass nestedRecord = PsiUtil.resolveClassInClassTypeOnly(psiType);
        if (nestedRecord == null || !nestedRecord.isRecord()) return;
        PsiDeconstructionList deconstructionList = deconstructionPattern.getDeconstructionList();
        PsiPattern[] components = deconstructionList.getDeconstructionComponents();
        if(components.length != nestedRecord.getRecordComponents().length) {
          return;
        }
        for (int i = 0; i < components.length; i++) {
          PsiPattern nestedDeconstructionPattern = components[i];
          PsiRecordComponent nestedComponent = nestedRecord.getRecordComponents()[i];
          collectDeconstructionRecordToDeleteUsagesConflict(nestedDeconstructionPattern, context, conflictDescriptions, nestedComponent);
        }
      }
    }

    private static boolean passUnchangedParameterToSuperCall(PsiMethodCallExpression superCall, int i, PsiReference ref) {
      return ArrayUtil.find(superCall.getArgumentList().getExpressions(), PsiUtil.skipParenthesizedExprUp(ref.getElement())) == i;
    }

    private static PsiMethodCallExpression getSuperCall(PsiMethod method, PsiCodeBlock body) {
      PsiStatement[] statements = body.getStatements();
      PsiStatement firstStmt = statements.length > 0 ? statements[0] : null;
      if (firstStmt instanceof PsiExpressionStatement) {
        PsiExpression call = ((PsiExpressionStatement)firstStmt).getExpression();
        if (call instanceof PsiMethodCallExpression &&
            MethodCallUtils.isSuperMethodCall((PsiMethodCallExpression)call, method)) {
          return (PsiMethodCallExpression)call;
        }
      }
      return null;
    }

    private void checkContract(MultiMap<PsiElement, @DialogMessage String> conflictDescriptions, PsiMethod method, boolean override) {
      try {
        ContractConverter.convertContract(method, myChangeInfo);
      }
      catch (ContractConverter.ContractInheritedException e) {
        if (!override) {
          conflictDescriptions.putValue(method, JavaRefactoringBundle.message("changeSignature.contract.converter.can.not.update.annotation", e.getMessage()));
        }
      }
      catch (ContractConverter.ContractConversionException e) {
        conflictDescriptions.putValue(method, JavaRefactoringBundle.message("changeSignature.contract.converter.can.not.update.annotation", e.getMessage()));
      }
    }

    private boolean needToChangeCalls() {
      return myChangeInfo.isNameChanged() || myChangeInfo.isParameterSetOrOrderChanged() || myChangeInfo.isExceptionSetOrOrderChanged();
    }


    private void addInaccessibilityDescriptions(Set<UsageInfo> usages,
                                                MultiMap<PsiElement, @DialogMessage String> conflictDescriptions) {
      PsiMethod method = myChangeInfo.getMethod();
      PsiModifierList modifierList = (PsiModifierList)method.getModifierList().copy();
      String visibility = myChangeInfo.getNewVisibility();
      VisibilityUtil.setVisibility(modifierList, visibility);

      searchForHierarchyConflicts(method, conflictDescriptions, visibility);
      boolean propagateVisibility = myChangeInfo instanceof JavaChangeInfoImpl && ((JavaChangeInfoImpl)myChangeInfo).isPropagateVisibility();

      for (Iterator<UsageInfo> iterator = usages.iterator(); iterator.hasNext();) {
        UsageInfo usageInfo = iterator.next();
        PsiElement element = usageInfo.getElement();
        PsiReference reference = usageInfo.getReference();
        if (!propagateVisibility && reference != null && !method.equals(reference.resolve())) continue;
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

    public static void searchForHierarchyConflicts(PsiMethod method,
                                                   MultiMap<PsiElement, @DialogMessage String> conflicts,
                                                   String modifier) {
      SuperMethodsSearch.search(method, ReadAction.compute(method::getContainingClass), true, false).forEach(
        new ReadActionProcessor<>() {
          @Override
          public boolean processInReadAction(MethodSignatureBackedByPsiMethod methodSignature) {
            final PsiMethod superMethod = methodSignature.getMethod();
            if (!hasCompatibleVisibility(superMethod, true, modifier)) {
              conflicts.putValue(superMethod, IntentionPowerPackBundle.message(
                "0.will.have.incompatible.access.privileges.with.super.1",
                RefactoringUIUtil.getDescription(method, false),
                RefactoringUIUtil.getDescription(superMethod, true)));
            }
            return true;
          }
        });
      OverridingMethodsSearch.search(method).forEach(new ReadActionProcessor<>() {
        @Override
        public boolean processInReadAction(PsiMethod overridingMethod) {
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
          return true;
        }
      });
    }

    private static boolean hasCompatibleVisibility(PsiMethod method, boolean isSuper, final String modifier) {
      return switch (modifier) {
        case PsiModifier.PRIVATE -> false;
        case PsiModifier.PACKAGE_LOCAL ->
          !isSuper || !(method.hasModifierProperty(PsiModifier.PUBLIC) || method.hasModifierProperty(PsiModifier.PROTECTED));
        case PsiModifier.PROTECTED -> isSuper ? !method.hasModifierProperty(PsiModifier.PUBLIC)
                                              : method.hasModifierProperty(PsiModifier.PROTECTED) ||
                                                method.hasModifierProperty(PsiModifier.PUBLIC);
        case PsiModifier.PUBLIC -> isSuper || method.hasModifierProperty(PsiModifier.PUBLIC);
        default -> throw new IllegalStateException("Unexpected value: " + modifier);
      };
    }

    private static boolean isVisibleFromOverridingMethod(PsiMethod method, PsiMethod overridingMethod, final String modifier) {
      final PsiModifierList modifierListCopy = (PsiModifierList)method.getModifierList().copy();
      modifierListCopy.setModifierProperty(modifier, true);
      return JavaResolveUtil.isAccessible(method, method.getContainingClass(), modifierListCopy, overridingMethod, null, null);
    }

    private PsiMethod addMethodConflicts(MultiMap<PsiElement, @DialogMessage String> conflicts) {
      String newMethodName = myChangeInfo.getNewName();
      try {
        final PsiMethod method = myChangeInfo.getMethod();
        if (!JavaLanguage.INSTANCE.equals(method.getLanguage())) return null;
        PsiManager manager = method.getManager();
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());
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
          if (JavaCodeStyleSettings.getInstance(method.getContainingFile()).GENERATE_FINAL_PARAMETERS) {
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

  private static void tryUpdateContracts(PsiMethod method, JavaChangeInfo info) {
    PsiAnnotation annotation = JavaMethodContractUtil.findContractAnnotation(method);
    if (annotation == null) return;
    try {
      PsiAnnotation newAnnotation = ContractConverter.convertContract(method, info);
      if (newAnnotation != null && !newAnnotation.isPhysical()) {
        annotation.replace(newAnnotation);
      }
    }
    catch (ContractConverter.ContractConversionException ignored) {
    }
  }

  sealed interface ChildWrapper permits VariableWrapper, PatternWrapper {
    @Nullable String getName();
    @Nullable PsiElement getNameIdentifier();
    @Nullable PsiTypeElement getTypeElement();
    default void normalizeDeclaration() {}
  }

  record VariableWrapper(@NotNull PsiVariable variable) implements ChildWrapper {

    @Override
    public String getName() {
      return variable.getName();
    }

    @Override
    public @Nullable PsiElement getNameIdentifier() {
      return variable.getNameIdentifier();
    }

    @Override
    public PsiTypeElement getTypeElement() {
      return variable.getTypeElement();
    }

    @Override
    public void normalizeDeclaration() {
      variable.normalizeDeclaration();
    }
  }

  record PatternWrapper(@NotNull PsiPattern pattern) implements ChildWrapper {

    @Override
    public @Nullable String getName() {
      final PsiPatternVariable variable = JavaPsiPatternUtil.getPatternVariable(pattern);
      return variable != null ? variable.getName() : null;
    }

    @Override
    public @Nullable PsiElement getNameIdentifier() {
      final PsiPatternVariable variable = JavaPsiPatternUtil.getPatternVariable(pattern);
      return variable != null ? variable.getNameIdentifier() : null;
    }

    @Override
    public @Nullable PsiTypeElement getTypeElement() {
      return JavaPsiPatternUtil.getPatternTypeElement(pattern);
    }
  }
}
