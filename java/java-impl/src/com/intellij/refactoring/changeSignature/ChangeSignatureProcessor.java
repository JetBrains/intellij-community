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
 * created at Sep 17, 2001
 * @author Jeka
 */
package com.intellij.refactoring.changeSignature;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.scope.processor.VariablesProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.psi.xml.XmlElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.JavaUnresolvableLocalCollisionDetector;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.*;
import com.intellij.refactoring.util.usageInfo.DefaultConstructorImplicitUsageInfo;
import com.intellij.refactoring.util.usageInfo.NoConstructorClassUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ChangeSignatureProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.changeSignature.ChangeSignatureProcessor");

  @Modifier private final String myNewVisibility;
  private final ChangeInfoImpl myChangeInfo;
  private final PsiManager myManager;
  private final PsiElementFactory myFactory;
  private final boolean myGenerateDelegate;
  private final Set<PsiMethod> myPropagateParametersMethods;
  private final Set<PsiMethod> myPropagateExceptionsMethods;

  public ChangeSignatureProcessor(Project project,
                                  PsiMethod method,
                                  final boolean generateDelegate,
                                  @Modifier String newVisibility,
                                  String newName,
                                  PsiType newType,
                                  @NotNull ParameterInfoImpl[] parameterInfo) {
    this(project, method, generateDelegate, newVisibility, newName,
         newType != null ? CanonicalTypes.createTypeWrapper(newType) : null,
         parameterInfo, null, null, null);
  }

  public ChangeSignatureProcessor(Project project,
                                  PsiMethod method,
                                  final boolean generateDelegate,
                                  String newVisibility,
                                  String newName,
                                  PsiType newType,
                                  ParameterInfoImpl[] parameterInfo,
                                  ThrownExceptionInfo[] exceptionInfos) {
    this(project, method, generateDelegate, newVisibility, newName,
         newType != null ? CanonicalTypes.createTypeWrapper(newType) : null,
         parameterInfo, exceptionInfos, null, null);
  }

  public ChangeSignatureProcessor(Project project,
                                  PsiMethod method,
                                  boolean generateDelegate,
                                  String newVisibility,
                                  String newName,
                                  CanonicalTypes.Type newType,
                                  @NotNull ParameterInfoImpl[] parameterInfo,
                                  ThrownExceptionInfo[] thrownExceptions,
                                  Set<PsiMethod> propagateParametersMethods,
                                  Set<PsiMethod> propagateExceptionsMethods) {
    super(project);
    myManager = PsiManager.getInstance(project);
    myFactory = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory();
    myGenerateDelegate = generateDelegate;

    myPropagateParametersMethods = propagateParametersMethods != null ? propagateParametersMethods : new HashSet<PsiMethod>();
    myPropagateExceptionsMethods = propagateExceptionsMethods != null ? propagateExceptionsMethods : new HashSet<PsiMethod>();

    LOG.assertTrue(method.isValid());
    if (newVisibility == null) {
      myNewVisibility = VisibilityUtil.getVisibilityModifier(method.getModifierList());
    } else {
      myNewVisibility = newVisibility;
    }

    myChangeInfo = new ChangeInfoImpl(myNewVisibility, method, newName, newType, parameterInfo, thrownExceptions);
    LOG.assertTrue(myChangeInfo.getMethod().isValid());
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new ChangeSignatureViewDescriptor(myChangeInfo.getMethod());
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();
    final PsiMethod method = myChangeInfo.getMethod();

    findSimpleUsages(method, result);

    final UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  private void findSimpleUsages(final PsiMethod method, final ArrayList<UsageInfo> result) {
    PsiMethod[] overridingMethods = findSimpleUsagesWithoutParameters(method, result, true, true, true);
    findUsagesInCallers (result);

    //Parameter name changes are not propagated
    findParametersUsage(method, result, overridingMethods);
  }

  private PsiMethod[] findSimpleUsagesWithoutParameters(final PsiMethod method,
                                                        final ArrayList<UsageInfo> result,
                                                        boolean isToModifyArgs,
                                                        boolean isToThrowExceptions,
                                                        boolean isOriginal) {

    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myProject);
    PsiMethod[] overridingMethods = OverridingMethodsSearch.search(method, method.getUseScope(), true).toArray(PsiMethod.EMPTY_ARRAY);

    for (PsiMethod overridingMethod : overridingMethods) {
      result.add(new OverriderUsageInfo(overridingMethod, method, isOriginal, isToModifyArgs, isToThrowExceptions));
    }

    boolean needToChangeCalls = !myGenerateDelegate && (myChangeInfo.isNameChanged || myChangeInfo.isParameterSetOrOrderChanged || myChangeInfo.isExceptionSetOrOrderChanged || myChangeInfo.isVisibilityChanged/*for checking inaccessible*/);
    if (needToChangeCalls) {
      int parameterCount = method.getParameterList().getParametersCount();

      PsiReference[] refs = MethodReferencesSearch.search(method, projectScope, true).toArray(PsiReference.EMPTY_ARRAY);
      for (PsiReference ref : refs) {
        PsiElement element = ref.getElement();
        boolean isToCatchExceptions = isToThrowExceptions && needToCatchExceptions(RefactoringUtil.getEnclosingMethod(element));
        if (!isToCatchExceptions) {
          if (RefactoringUtil.isMethodUsage(element)) {
            PsiExpressionList list = RefactoringUtil.getArgumentListByMethodReference(element);
            if (!method.isVarArgs() && list.getExpressions().length != parameterCount) continue;
          }
        }
        if (RefactoringUtil.isMethodUsage(element)) {
          result.add(new MethodCallUsageInfo(element, isToModifyArgs, isToCatchExceptions));
        }
        else if (element instanceof PsiDocTagValue) {
          result.add(new UsageInfo(ref.getElement()));
        }
        else if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) {
          DefaultConstructorImplicitUsageInfo implicitUsageInfo = new DefaultConstructorImplicitUsageInfo((PsiMethod)element,
                                                                                                          ((PsiMethod)element).getContainingClass(), method);
          result.add(implicitUsageInfo);
        }
        else if(element instanceof PsiClass) {
          LOG.assertTrue(method.isConstructor());
          final PsiClass psiClass = (PsiClass)element;
          if (shouldPropagateToNonPhysicalMethod(method, result, psiClass, myPropagateParametersMethods)) continue;
          if (shouldPropagateToNonPhysicalMethod(method, result, psiClass, myPropagateExceptionsMethods)) continue;
          result.add(new NoConstructorClassUsageInfo(psiClass));
        }
        else if (ref instanceof PsiCallReference) {
          result.add(new CallReferenceUsageInfo((PsiCallReference) ref));
        }
        else {
          result.add(new MoveRenameUsageInfo(element, ref, method));
        }
      }

      //if (method.isConstructor() && parameterCount == 0) {
      //    RefactoringUtil.visitImplicitConstructorUsages(method.getContainingClass(),
      //                                                   new DefaultConstructorUsageCollector(result));
      //}
    }
    else if (myChangeInfo.isParameterTypesChanged) {
      PsiReference[] refs = MethodReferencesSearch.search(method, projectScope, true).toArray(PsiReference.EMPTY_ARRAY);
      for (PsiReference reference : refs) {
        final PsiElement element = reference.getElement();
        if (element instanceof PsiDocTagValue) {
          result.add(new UsageInfo(reference));
        }
        else if (element instanceof XmlElement) {
          result.add(new MoveRenameUsageInfo(reference, method));
        }
      }
    }

    // Conflicts
    detectLocalsCollisionsInMethod(method, result, isOriginal);
    for (final PsiMethod overridingMethod : overridingMethods) {
      detectLocalsCollisionsInMethod(overridingMethod, result, isOriginal);
    }

    return overridingMethods;
  }

  private static boolean shouldPropagateToNonPhysicalMethod(PsiMethod method, ArrayList<UsageInfo> result, PsiClass containingClass, final Set<PsiMethod> propagateMethods) {
    for (PsiMethod psiMethod : propagateMethods) {
      if (!psiMethod.isPhysical() && Comparing.strEqual(psiMethod.getName(), containingClass.getName())) {
        result.add(new DefaultConstructorImplicitUsageInfo(psiMethod, containingClass, method));
        return true;
      }
    }
    return false;
  }

  private void findUsagesInCallers(final ArrayList<UsageInfo> usages) {
    for (PsiMethod caller : myPropagateParametersMethods) {
      usages.add(new CallerUsageInfo(caller, true, myPropagateExceptionsMethods.contains(caller)));
    }
    for (PsiMethod caller : myPropagateExceptionsMethods) {
      usages.add(new CallerUsageInfo(caller, myPropagateParametersMethods.contains(caller), true));
    }
    Set<PsiMethod> merged = new HashSet<PsiMethod>();
    merged.addAll(myPropagateParametersMethods);
    merged.addAll(myPropagateExceptionsMethods);
    for (final PsiMethod method : merged) {
      findSimpleUsagesWithoutParameters(method, usages, myPropagateParametersMethods.contains(method),
                                        myPropagateExceptionsMethods.contains(method), false);
    }
  }

  private boolean needToChangeCalls() {
    return myChangeInfo.isNameChanged || myChangeInfo.isParameterSetOrOrderChanged || myChangeInfo.isExceptionSetOrOrderChanged;
  }

  private boolean needToCatchExceptions(PsiMethod caller) {
    return myChangeInfo.isExceptionSetOrOrderChanged && !myPropagateExceptionsMethods.contains(caller);
  }

  private void detectLocalsCollisionsInMethod(final PsiMethod method,
                                              final ArrayList<UsageInfo> result,
                                              boolean isOriginal) {
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final Set<PsiParameter> deletedOrRenamedParameters = new HashSet<PsiParameter>();
    if (isOriginal) {
      deletedOrRenamedParameters.addAll(Arrays.asList(parameters));
      for (ParameterInfoImpl parameterInfo : myChangeInfo.newParms) {
        if (parameterInfo.oldParameterIndex >= 0) {
          final PsiParameter parameter = parameters[parameterInfo.oldParameterIndex];
          if (parameterInfo.getName().equals(parameter.getName())) {
            deletedOrRenamedParameters.remove(parameter);
          }
        }
      }
    }

    for (ParameterInfoImpl parameterInfo : myChangeInfo.newParms) {
      final int oldParameterIndex = parameterInfo.oldParameterIndex;
      final String newName = parameterInfo.getName();
      if (oldParameterIndex >= 0) {
        if (isOriginal) {   //Name changes take place only in primary method
          final PsiParameter parameter = parameters[oldParameterIndex];
          if (!newName.equals(parameter.getName())) {
            JavaUnresolvableLocalCollisionDetector.visitLocalsCollisions(parameter, newName, method.getBody(), null, new JavaUnresolvableLocalCollisionDetector.CollidingVariableVisitor() {
              public void visitCollidingElement(final PsiVariable collidingVariable) {
                if (!deletedOrRenamedParameters.contains(collidingVariable)) {
                  result.add(new RenamedParameterCollidesWithLocalUsageInfo(parameter, collidingVariable, method));
                }
              }
            });
          }
        }
      }
      else {
        JavaUnresolvableLocalCollisionDetector.visitLocalsCollisions(method, newName, method.getBody(), null, new JavaUnresolvableLocalCollisionDetector.CollidingVariableVisitor() {
          public void visitCollidingElement(PsiVariable collidingVariable) {
            if (!deletedOrRenamedParameters.contains(collidingVariable)) {
              result.add(new NewParameterCollidesWithLocalUsageInfo(collidingVariable, collidingVariable, method));
            }
          }
        });
      }
    }
  }

  private void findParametersUsage(final PsiMethod method, ArrayList<UsageInfo> result, PsiMethod[] overriders) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    for (ParameterInfoImpl info : myChangeInfo.newParms) {
      if (info.oldParameterIndex >= 0) {
        PsiParameter parameter = parameters[info.oldParameterIndex];
        if (!info.getName().equals(parameter.getName())) {
          addParameterUsages(parameter, result, info);

          for (PsiMethod overrider : overriders) {
            PsiParameter parameter1 = overrider.getParameterList().getParameters()[info.oldParameterIndex];
            if (parameter.getName().equals(parameter1.getName())) {
              addParameterUsages(parameter1, result, info);
            }
          }
        }
      }
    }
  }

  protected void refreshElements(PsiElement[] elements) {
    boolean condition = elements.length == 1 && elements[0] instanceof PsiMethod;
    LOG.assertTrue(condition);
    myChangeInfo.updateMethod((PsiMethod) elements[0]);
  }

  private void addMethodConflicts(MultiMap<PsiElement, String> conflicts) {
    String newMethodName = myChangeInfo.newName;

    try {
      PsiMethod prototype;
      PsiManager manager = PsiManager.getInstance(myProject);
      PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
      final PsiMethod method = myChangeInfo.getMethod();
      final CanonicalTypes.Type returnType = myChangeInfo.newReturnType;
      if (returnType != null) {
        prototype = factory.createMethod(newMethodName, returnType.getType(method, manager));
      }
      else {
        prototype = factory.createConstructor();
        prototype.setName(newMethodName);
      }
      ParameterInfoImpl[] parameters = myChangeInfo.newParms;


      for (ParameterInfoImpl info : parameters) {
        final PsiType parameterType = info.createType(method, manager);
        PsiParameter param = factory.createParameter(info.getName(), parameterType);
        prototype.getParameterList().add(param);
      }

      ConflictsUtil.checkMethodConflicts(
        method.getContainingClass(),
        method,
        prototype, conflicts);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }


  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    MultiMap<PsiElement, String> conflictDescriptions = new MultiMap<PsiElement, String>();
    UsageInfo[] usagesIn = refUsages.get();
    addMethodConflicts(conflictDescriptions);
    RenameUtil.addConflictDescriptions(usagesIn, conflictDescriptions);
    Set<UsageInfo> usagesSet = new HashSet<UsageInfo>(Arrays.asList(usagesIn));
    RenameUtil.removeConflictUsages(usagesSet);
    if (myChangeInfo.isVisibilityChanged) {
      try {
        addInaccessibilityDescriptions(usagesSet, conflictDescriptions);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    if (myPrepareSuccessfulSwingThreadCallback != null && !conflictDescriptions.isEmpty()) {
      ConflictsDialog dialog = new ConflictsDialog(myProject, conflictDescriptions);
      dialog.show();
      if (!dialog.isOK()){
        if (dialog.isShowConflicts()) prepareSuccessful();
        return false;
      }
    }

    if (myChangeInfo.isReturnTypeChanged) {
      askToRemoveCovariantOverriders (usagesSet);
    }

    refUsages.set(usagesSet.toArray(new UsageInfo[usagesSet.size()]));
    prepareSuccessful();
    return true;
  }

  private void addInaccessibilityDescriptions(Set<UsageInfo> usages, MultiMap<PsiElement, String> conflictDescriptions) throws IncorrectOperationException {
    PsiMethod method = myChangeInfo.getMethod();
    PsiModifierList modifierList = (PsiModifierList)method.getModifierList().copy();
    VisibilityUtil.setVisibility(modifierList, myNewVisibility);

    for (Iterator<UsageInfo> iterator = usages.iterator(); iterator.hasNext();) {
      UsageInfo usageInfo = iterator.next();
      PsiElement element = usageInfo.getElement();
      if (element != null) {
        if (element instanceof PsiReferenceExpression) {
          PsiClass accessObjectClass = null;
          PsiExpression qualifier = ((PsiReferenceExpression)element).getQualifierExpression();
          if (qualifier != null) {
            accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass(qualifier).getElement();
          }

          if (!JavaPsiFacade.getInstance(element.getProject()).getResolveHelper()
            .isAccessible(method, modifierList, element, accessObjectClass, null)) {
            String message =
              RefactoringBundle.message("0.with.1.visibility.is.not.accesible.from.2",
                                        RefactoringUIUtil.getDescription(method, true),
                                        myNewVisibility,
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

  private void askToRemoveCovariantOverriders(Set<UsageInfo> usages) {
    if (PsiUtil.isLanguageLevel5OrHigher(myChangeInfo.getMethod())) {
      List<UsageInfo> covariantOverriderInfos = new ArrayList<UsageInfo>();
      for (UsageInfo usageInfo : usages) {
        if (usageInfo instanceof OverriderUsageInfo) {
          final OverriderUsageInfo info = (OverriderUsageInfo)usageInfo;
          PsiMethod overrider = info.getElement();
          PsiMethod baseMethod = info.getBaseMethod();
          PsiSubstitutor substitutor = calculateSubstitutor(overrider, baseMethod);
          PsiType type;
          try {
            type = substitutor.substitute(myChangeInfo.newReturnType.getType(myChangeInfo.getMethod(), myManager));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
            return;
          }
          final PsiType overriderType = overrider.getReturnType();
          if (overriderType != null && type.isAssignableFrom(overriderType)) {
            covariantOverriderInfos.add(usageInfo);
          }
        }
      }

      // to be able to do filtering
      preprocessCovariantOverriders(covariantOverriderInfos);

      if (!covariantOverriderInfos.isEmpty()) {
        if (ApplicationManager.getApplication().isUnitTestMode() || !isProcessCovariantOverriders()) {
          for (UsageInfo usageInfo : covariantOverriderInfos) {
            usages.remove(usageInfo);
          }
        }
      }
    }
  }

  protected void preprocessCovariantOverriders(final List<UsageInfo> covariantOverriderInfos) {
  }

  protected boolean isProcessCovariantOverriders() {
    return Messages.showYesNoDialog(myProject, RefactoringBundle.message("do.you.want.to.process.overriding.methods.with.covariant.return.type"),
                             JavaChangeSignatureHandler.REFACTORING_NAME, Messages.getQuestionIcon())
    == DialogWrapper.OK_EXIT_CODE;
  }

  protected void performRefactoring(UsageInfo[] usages) {
    PsiElementFactory factory = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory();

    try {
      if (myChangeInfo.isNameChanged) {
        myChangeInfo.newNameIdentifier = factory.createIdentifier(myChangeInfo.newName);
      }

      if (myChangeInfo.isReturnTypeChanged) {
        myChangeInfo.newTypeElement = myChangeInfo.newReturnType.getType(myChangeInfo.getMethod(), myManager);
      }

      if (myGenerateDelegate) {
        generateDelegate();
      }

      for (UsageInfo usage : usages) {
        if (usage instanceof CallerUsageInfo) {
          final CallerUsageInfo callerUsageInfo = (CallerUsageInfo)usage;
          processCallerMethod(callerUsageInfo.getMethod(), null, callerUsageInfo.isToInsertParameter(),
                              callerUsageInfo.isToInsertException());
        }
        else if (usage instanceof OverriderUsageInfo) {
          OverriderUsageInfo info = (OverriderUsageInfo)usage;
          final PsiMethod method = info.getElement();
          final PsiMethod baseMethod = info.getBaseMethod();
          if (info.isOriginalOverrider()) {
            processPrimaryMethod(method, baseMethod, false);
          }
          else {
            processCallerMethod(method, baseMethod, info.isToInsertArgs(), info.isToCatchExceptions());
          }
        }
      }

      LOG.assertTrue(myChangeInfo.getMethod().isValid());
      processPrimaryMethod(myChangeInfo.getMethod(), null, true);
      List<UsageInfo> postponedUsages = new ArrayList<UsageInfo>();

      for (UsageInfo usage : usages) {
        PsiElement element = usage.getElement();
        if (element == null) continue;

        if (usage instanceof DefaultConstructorImplicitUsageInfo) {
          final DefaultConstructorImplicitUsageInfo defConstructorUsage = (DefaultConstructorImplicitUsageInfo)usage;
          PsiMethod constructor = defConstructorUsage.getConstructor();
          if (!constructor.isPhysical()) {
            final boolean toPropagate = myPropagateParametersMethods.remove(constructor);
            final PsiClass containingClass = defConstructorUsage.getContainingClass();
            constructor = (PsiMethod)containingClass.add(constructor);
            PsiUtil.setModifierProperty(constructor, VisibilityUtil.getVisibilityModifier(containingClass.getModifierList()), true);
            if (toPropagate) {
              myPropagateParametersMethods.add(constructor);
            }
          }
          addSuperCall(constructor, defConstructorUsage.getBaseConstructor(),usages);
        }
        else if (usage instanceof NoConstructorClassUsageInfo) {
          addDefaultConstructor(((NoConstructorClassUsageInfo)usage).getPsiClass(),usages);
        }
        else if (element instanceof PsiJavaCodeReferenceElement) {
          if (usage instanceof MethodCallUsageInfo) {
            final MethodCallUsageInfo methodCallInfo = (MethodCallUsageInfo)usage;
            processMethodUsage(methodCallInfo.getElement(), myChangeInfo, methodCallInfo.isToChangeArguments(),
                               methodCallInfo.isToCatchExceptions(), methodCallInfo.getReferencedMethod(), usages);
          }
          else if (usage instanceof MyParameterUsageInfo) {
            String newName = ((MyParameterUsageInfo)usage).newParameterName;
            String oldName = ((MyParameterUsageInfo)usage).oldParameterName;
            processParameterUsage((PsiReferenceExpression)element, oldName, newName);
          } else {
            postponedUsages.add(usage);
          }
        }
        else if (usage instanceof CallReferenceUsageInfo) {
          ((CallReferenceUsageInfo) usage).getReference().handleChangeSignature(myChangeInfo);
        }
        else if (element instanceof PsiEnumConstant) {
          fixActualArgumentsList(((PsiEnumConstant)element).getArgumentList(), myChangeInfo, true);
        }
        else if (!(usage instanceof OverriderUsageInfo)) {
          postponedUsages.add(usage);
        }
      }

      for (UsageInfo usageInfo : postponedUsages) {
        PsiElement element = usageInfo.getElement();
        if (element == null) continue;
        PsiReference reference = usageInfo instanceof MoveRenameUsageInfo ?
                                 usageInfo.getReference() :
                                 element.getReference();
        if (reference != null) {
          PsiElement target = null;
          if (usageInfo instanceof MyParameterUsageInfo) {
            String newParameterName = ((MyParameterUsageInfo)usageInfo).newParameterName;
            PsiParameter[] newParams = myChangeInfo.getMethod().getParameterList().getParameters();
            for (PsiParameter newParam : newParams) {
              if (newParam.getName().equals(newParameterName)) {
                target = newParam;
                break;
              }
            }
          }
          else {
            target = myChangeInfo.getMethod();
          }
          if (target != null) {
            reference.bindToElement(target);
          }
        }
      }

      LOG.assertTrue(myChangeInfo.getMethod().isValid());
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void generateDelegate() throws IncorrectOperationException {
    final PsiMethod delegate = (PsiMethod)myChangeInfo.getMethod().copy();
    final PsiClass targetClass = myChangeInfo.getMethod().getContainingClass();
    LOG.assertTrue(!targetClass.isInterface());
    makeEmptyBody(myFactory, delegate);
    final PsiCallExpression callExpression = addDelegatingCallTemplate(delegate, myChangeInfo.newName);
    addDelegateArguments(callExpression);
    targetClass.addBefore(delegate, myChangeInfo.getMethod());
  }

  private void addDelegateArguments(final PsiCallExpression callExpression) throws IncorrectOperationException {
    final ParameterInfoImpl[] newParms = myChangeInfo.newParms;
    for (int i = 0; i < newParms.length; i++) {
      ParameterInfoImpl newParm = newParms[i];
      final PsiExpression actualArg;
      if (newParm.oldParameterIndex >= 0) {
        actualArg = myFactory.createExpressionFromText(myChangeInfo.oldParameterNames[newParm.oldParameterIndex], callExpression);
      }
      else {
        actualArg = myChangeInfo.getValue(i, callExpression);
      }
      callExpression.getArgumentList().add(actualArg);
    }
  }

  public static void makeEmptyBody(final PsiElementFactory factory, final PsiMethod delegate) throws IncorrectOperationException {
    PsiCodeBlock body = delegate.getBody();
    if (body != null) {
      body.replace(factory.createCodeBlock());
    }
    else {
      delegate.add(factory.createCodeBlock());
    }
    PsiUtil.setModifierProperty(delegate, PsiModifier.ABSTRACT, false);
  }

  public static PsiCallExpression addDelegatingCallTemplate(final PsiMethod delegate, final String newName) throws IncorrectOperationException {
    Project project = delegate.getProject();
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    PsiCodeBlock body = delegate.getBody();
    assert body != null;
    final PsiCallExpression callExpression;
    if (delegate.isConstructor()) {
      PsiElement callStatement = factory.createStatementFromText("this();", null);
      callStatement = CodeStyleManager.getInstance(project).reformat(callStatement);
      callStatement = body.add(callStatement);
      callExpression = (PsiCallExpression)((PsiExpressionStatement) callStatement).getExpression();
    } else {
      if (PsiType.VOID.equals(delegate.getReturnType())) {
        PsiElement callStatement = factory.createStatementFromText(newName + "();", null);
        callStatement = CodeStyleManager.getInstance(project).reformat(callStatement);
        callStatement = body.add(callStatement);
        callExpression = (PsiCallExpression)((PsiExpressionStatement) callStatement).getExpression();
      }
      else {
        PsiElement callStatement = factory.createStatementFromText("return " + newName + "();", null);
        callStatement = CodeStyleManager.getInstance(project).reformat(callStatement);
        callStatement = body.add(callStatement);
        callExpression = (PsiCallExpression)((PsiReturnStatement) callStatement).getReturnValue();
      }
    }
    return callExpression;
  }

  private void addDefaultConstructor(PsiClass aClass, final UsageInfo[] usages) throws IncorrectOperationException {
    if (!(aClass instanceof PsiAnonymousClass)) {
      PsiMethod defaultConstructor = myFactory.createMethodFromText(aClass.getName() + "(){}", aClass);
      defaultConstructor = (PsiMethod) CodeStyleManager.getInstance(myProject).reformat(defaultConstructor);
      defaultConstructor = (PsiMethod) aClass.add(defaultConstructor);
      PsiUtil.setModifierProperty(defaultConstructor, VisibilityUtil.getVisibilityModifier(aClass.getModifierList()), true);
      addSuperCall(defaultConstructor, null, usages);
    } else {
      final PsiElement parent = aClass.getParent();
      if (parent instanceof PsiNewExpression) {
        final PsiExpressionList argumentList = ((PsiNewExpression) parent).getArgumentList();
        fixActualArgumentsList(argumentList, myChangeInfo, true);
      }
    }
  }

  private void addSuperCall(PsiMethod constructor, PsiMethod callee, final UsageInfo[] usages) throws IncorrectOperationException {
    PsiExpressionStatement superCall = (PsiExpressionStatement) myFactory.createStatementFromText("super();", constructor);
    PsiCodeBlock body = constructor.getBody();
    assert body != null;
    PsiStatement[] statements = body.getStatements();
    if (statements.length > 0) {
      superCall = (PsiExpressionStatement) body.addBefore(superCall, statements[0]);
    } else {
      superCall = (PsiExpressionStatement) body.add(superCall);
    }
    PsiMethodCallExpression callExpression = (PsiMethodCallExpression) superCall.getExpression();
    processMethodUsage(callExpression.getMethodExpression(), myChangeInfo, true, false, callee, usages);
  }

  private PsiParameter createNewParameter(ParameterInfoImpl newParm,
                                          PsiSubstitutor substitutor) throws IncorrectOperationException {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
    final PsiType type = substitutor.substitute(newParm.createType(myChangeInfo.getMethod().getParameterList(), myManager));
    return factory.createParameter(newParm.getName(), type);
  }

  protected String getCommandName() {
    return RefactoringBundle.message("changing.signature.of.0", UsageViewUtil.getDescriptiveName(myChangeInfo.getMethod()));
  }

  private void processMethodUsage(PsiElement ref,
                                  ChangeInfoImpl changeInfo,
                                  boolean toChangeArguments,
                                  boolean toCatchExceptions,
                                  PsiMethod callee, final UsageInfo[] usages) throws IncorrectOperationException {
    if (changeInfo.isNameChanged) {
      if (ref instanceof PsiJavaCodeReferenceElement) {
        PsiElement last = ((PsiJavaCodeReferenceElement)ref).getReferenceNameElement();
        if (last instanceof PsiIdentifier && last.getText().equals(changeInfo.oldName)) {
          last.replace(changeInfo.newNameIdentifier);
        }
      }
    }

    final PsiMethod caller = RefactoringUtil.getEnclosingMethod(ref);
    if (toChangeArguments) {
      final PsiExpressionList list = RefactoringUtil.getArgumentListByMethodReference(ref);
      boolean toInsertDefaultValue = !myPropagateParametersMethods.contains(caller);
      if (toInsertDefaultValue && ref instanceof PsiReferenceExpression) {
        final PsiExpression qualifierExpression = ((PsiReferenceExpression) ref).getQualifierExpression();
        if (qualifierExpression instanceof PsiSuperExpression && callerSignatureIsAboutToChangeToo(caller, usages)) {
          toInsertDefaultValue = false;
        }
      }

      fixActualArgumentsList(list, changeInfo, toInsertDefaultValue);
    }

    if (toCatchExceptions) {
      if (!(ref instanceof PsiReferenceExpression && ((PsiReferenceExpression)ref).getQualifierExpression() instanceof PsiSuperExpression)) {
        if (needToCatchExceptions(caller)) {
          PsiClassType[] newExceptions = callee != null ? getCalleeChangedExceptionInfo(callee) : getPrimaryChangedExceptionInfo(changeInfo);
          fixExceptions(ref, newExceptions);
        }
      }
    }
  }

  private static boolean callerSignatureIsAboutToChangeToo(final PsiMethod caller, final UsageInfo[] usages) {
    for (UsageInfo usage : usages) {
      if (usage instanceof MethodCallUsageInfo && MethodSignatureUtil.isSuperMethod(((MethodCallUsageInfo)usage).getReferencedMethod(), caller)) return true;
    }
    return false;
  }

  private static PsiClassType[] getCalleeChangedExceptionInfo(final PsiMethod callee) {
    return callee.getThrowsList().getReferencedTypes(); //Callee method's throws list is already modified!
  }

  private PsiClassType[] getPrimaryChangedExceptionInfo(ChangeInfoImpl changeInfo) throws IncorrectOperationException {
    PsiClassType[] newExceptions = new PsiClassType[changeInfo.newExceptions.length];
    for (int i = 0; i < newExceptions.length; i++) {
      newExceptions[i] = (PsiClassType)changeInfo.newExceptions[i].myType.getType(myChangeInfo.getMethod(), myManager); //context really does not matter here
    }
    return newExceptions;
  }

  private void fixExceptions(PsiElement ref, PsiClassType[] newExceptions) throws IncorrectOperationException {
    //methods' throws lists are already modified, may use ExceptionUtil.collectUnhandledExceptions
    newExceptions = filterCheckedExceptions(newExceptions);

    PsiElement context = PsiTreeUtil.getParentOfType(ref, PsiTryStatement.class, PsiMethod.class);
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

      PsiClassType[] exceptionsToAdd  = filterUnhandledExceptions(newExceptions, tryBlock);
      addExceptions(exceptionsToAdd, tryStatement);

      adjustPossibleEmptyTryStatement(tryStatement);
    }
    else {
      newExceptions = filterUnhandledExceptions(newExceptions, ref);
      if (newExceptions.length > 0) {
        //Add new try statement
        PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory();
        PsiTryStatement tryStatement = (PsiTryStatement)elementFactory.createStatementFromText("try {} catch (Exception e) {}", null);
        PsiStatement anchor = PsiTreeUtil.getParentOfType(ref, PsiStatement.class);
        LOG.assertTrue(anchor != null);
        tryStatement.getTryBlock().add(anchor);
        tryStatement = (PsiTryStatement)anchor.getParent().addAfter(tryStatement, anchor);

        addExceptions(newExceptions, tryStatement);
        anchor.delete();
        tryStatement.getCatchSections()[0].delete(); //Delete dummy catch section
      }
    }
  }

  private static PsiClassType[] filterCheckedExceptions(PsiClassType[] exceptions) {
    List<PsiClassType> result = new ArrayList<PsiClassType>();
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

  private void fixPrimaryThrowsLists(PsiMethod method, PsiClassType[] newExceptions) throws IncorrectOperationException {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory();
    PsiJavaCodeReferenceElement[] refs = new PsiJavaCodeReferenceElement[newExceptions.length];
    for (int i = 0; i < refs.length; i++) {
      refs[i] = elementFactory.createReferenceElementByType(newExceptions[i]);
    }
    PsiReferenceList throwsList = elementFactory.createReferenceList(refs);

    replaceThrowsList(method, throwsList);
  }

  private void replaceThrowsList(PsiMethod method, PsiReferenceList throwsList) throws IncorrectOperationException {
    PsiReferenceList methodThrowsList = (PsiReferenceList)method.getThrowsList().replace(throwsList);
    methodThrowsList = (PsiReferenceList)JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(methodThrowsList);
    myManager.getCodeStyleManager().reformatRange(method, method.getParameterList().getTextRange().getEndOffset(),
                                                  methodThrowsList.getTextRange().getEndOffset());
  }

  private static PsiClassType[] filterUnhandledExceptions(PsiClassType[] exceptions, PsiElement place) {
    List<PsiClassType> result = new ArrayList<PsiClassType>();
    for (PsiClassType exception : exceptions) {
      if (!ExceptionUtil.isHandled(exception, place)) result.add(exception);
    }
    return result.toArray(new PsiClassType[result.size()]);
  }

  private static boolean isCatchParameterRedundant (PsiClassType catchParamType, Collection<PsiClassType> thrownTypes) {
    for (PsiType exceptionType : thrownTypes) {
      if (exceptionType.isConvertibleFrom(catchParamType)) return false;
    }
    return true;
  }

  private static int getNonVarargCount(ChangeInfoImpl changeInfo, PsiExpression[] args) {
    if (!changeInfo.wasVararg) return args.length;
    return changeInfo.oldParameterTypes.length - 1;
  }

  //This methods works equally well for primary usages as well as for propagated callers' usages
  private void fixActualArgumentsList(PsiExpressionList list,
                                      ChangeInfoImpl changeInfo,
                                      boolean toInsertDefaultValue) throws IncorrectOperationException {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(list.getProject()).getElementFactory();
    if (changeInfo.isParameterSetOrOrderChanged) {
      if (changeInfo.isPropagationEnabled) {
        final ParameterInfoImpl[] createdParmsInfo = changeInfo.getCreatedParmsInfoWithoutVarargs();
        for (ParameterInfoImpl info : createdParmsInfo) {
          PsiExpression newArg;
          if (toInsertDefaultValue) {
            newArg = createDefaultValue(factory, info, list);
          }
          else {
            newArg = factory.createExpressionFromText(info.getName(), list);
          }
          list.add(newArg);
        }
      }
      else {
        final PsiExpression[] args = list.getExpressions();
        final int nonVarargCount = getNonVarargCount(changeInfo, args);
        final int varargCount = args.length - nonVarargCount;
        PsiExpression[] newVarargInitializers = null;

        final int newArgsLength;
        final int newNonVarargCount;
        if (changeInfo.arrayToVarargs) {
          newNonVarargCount = changeInfo.newParms.length - 1;
          final ParameterInfoImpl lastNewParm = changeInfo.newParms[changeInfo.newParms.length - 1];
          final PsiExpression arrayToConvert = args[lastNewParm.oldParameterIndex];
          if (arrayToConvert instanceof PsiNewExpression) {
            final PsiNewExpression expression = (PsiNewExpression)arrayToConvert;
            final PsiArrayInitializerExpression arrayInitializer = expression.getArrayInitializer();
            if (arrayInitializer != null) {
              newVarargInitializers = arrayInitializer.getInitializers();
            }
          }
          newArgsLength = newVarargInitializers == null ? changeInfo.newParms.length : newNonVarargCount + newVarargInitializers.length;
        }
        else if (changeInfo.retainsVarargs) {
          newNonVarargCount = changeInfo.newParms.length - 1;
          newArgsLength =  newNonVarargCount + varargCount;
        }
        else if (changeInfo.obtainsVarags) {
          newNonVarargCount = changeInfo.newParms.length - 1;
          newArgsLength = newNonVarargCount;
        }
        else {
          newNonVarargCount = changeInfo.newParms.length;
          newArgsLength = changeInfo.newParms.length;
        }
        final PsiExpression[] newArgs = new PsiExpression[newArgsLength];
        for (int i = 0; i < newNonVarargCount; i++) {
          newArgs [i] = createActualArgument(list, changeInfo.newParms [i], toInsertDefaultValue, args);
        }
        if (changeInfo.arrayToVarargs) {
          if (newVarargInitializers == null) {
            newArgs [newNonVarargCount] = createActualArgument(list, changeInfo.newParms [newNonVarargCount], toInsertDefaultValue, args);
          }
          else {
            System.arraycopy(newVarargInitializers, 0, newArgs, newNonVarargCount, newVarargInitializers.length);
          }
        }
        else {
          final int newVarargCount = newArgsLength - newNonVarargCount;
          LOG.assertTrue(newVarargCount == 0 || newVarargCount == varargCount);
          System.arraycopy(args, nonVarargCount, newArgs, newNonVarargCount, newVarargCount);
        }
        ChangeSignatureUtil.synchronizeList(list, Arrays.asList(newArgs), ExpressionList.INSTANCE, changeInfo.toRemoveParm);
      }
    }
  }

  private PsiExpression createActualArgument(final PsiExpressionList list, final ParameterInfoImpl info, final boolean toInsertDefaultValue,
                                             final PsiExpression[] args) throws IncorrectOperationException {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(list.getProject()).getElementFactory();
    final int index = info.oldParameterIndex;
    if (index >= 0) {
      return args[index];
    } else {
      if (toInsertDefaultValue) {
        return createDefaultValue(factory, info, list);
      } else {
        return factory.createExpressionFromText(info.getName(), list);
      }
    }
  }

  @Nullable
  private PsiExpression createDefaultValue(final PsiElementFactory factory, final ParameterInfoImpl info, final PsiExpressionList list)
    throws IncorrectOperationException {
    if (info.useAnySingleVariable) {
      final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(list.getProject()).getResolveHelper();
      final PsiType type = info.getTypeWrapper().getType(myChangeInfo.getMethod(), myManager);
      final VariablesProcessor processor = new VariablesProcessor(false) {
              protected boolean check(PsiVariable var, ResolveState state) {
                if (var instanceof PsiField && !resolveHelper.isAccessible((PsiField)var, list, null)) return false;
                final PsiType varType = state.get(PsiSubstitutor.KEY).substitute(var.getType());
                return type.isAssignableFrom(varType);
              }

              public boolean execute(PsiElement pe, ResolveState state) {
                super.execute(pe, state);
                return size() < 2;
              }
            };
      PsiScopesUtil.treeWalkUp(processor, list, null);
      if (processor.size() == 1) {
        final PsiVariable result = processor.getResult(0);
        return factory.createExpressionFromText(result.getName(), list);
      }
    }
    final PsiCallExpression callExpression = PsiTreeUtil.getParentOfType(list, PsiCallExpression.class);
    return callExpression != null ? info.getValue(callExpression) : factory.createExpressionFromText(info.defaultValue, list);
  }

  private static void addParameterUsages(PsiParameter parameter,
                                  ArrayList<UsageInfo> results,
                                  ParameterInfoImpl info) {
    PsiManager manager = parameter.getManager();
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(manager.getProject());
    for (PsiReference psiReference : ReferencesSearch.search(parameter, projectScope, false)) {
      PsiElement parmRef = psiReference.getElement();
      UsageInfo usageInfo = new MyParameterUsageInfo(parmRef, parameter.getName(), info.getName());
      results.add(usageInfo);
    }
  }

  private void processCallerMethod(PsiMethod caller,
                                   PsiMethod baseMethod,
                                   boolean toInsertParams,
                                   boolean toInsertThrows) throws IncorrectOperationException {
    LOG.assertTrue(toInsertParams || toInsertThrows);
    if (toInsertParams) {
      List<PsiParameter> newParameters = new ArrayList<PsiParameter>();
      newParameters.addAll(Arrays.asList(caller.getParameterList().getParameters()));
      final ParameterInfoImpl[] primaryNewParms = myChangeInfo.newParms;
      PsiSubstitutor substitutor = baseMethod == null ? PsiSubstitutor.EMPTY : calculateSubstitutor(caller, baseMethod);
      for (ParameterInfoImpl info : primaryNewParms) {
        if (info.oldParameterIndex < 0) newParameters.add(createNewParameter(info, substitutor));
      }
      PsiParameter[] arrayed = newParameters.toArray(new PsiParameter[newParameters.size()]);
      boolean[] toRemoveParm = new boolean[arrayed.length];
      Arrays.fill(toRemoveParm, false);
      resolveParameterVsFieldsConflicts(arrayed, caller, caller.getParameterList(), toRemoveParm);
    }

    if (toInsertThrows) {
      List<PsiJavaCodeReferenceElement> newThrowns = new ArrayList<PsiJavaCodeReferenceElement>();
      final PsiReferenceList throwsList = caller.getThrowsList();
      newThrowns.addAll(Arrays.asList(throwsList.getReferenceElements()));
      final ThrownExceptionInfo[] primaryNewExns = myChangeInfo.newExceptions;
      for (ThrownExceptionInfo thrownExceptionInfo : primaryNewExns) {
        if (thrownExceptionInfo.oldIndex < 0) {
          final PsiClassType type = (PsiClassType)thrownExceptionInfo.createType(caller, myManager);
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

  private void processPrimaryMethod(PsiMethod method,
                                    PsiMethod baseMethod,
                                    boolean isOriginal) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();

    if (myChangeInfo.isVisibilityChanged) {
      PsiModifierList modifierList = method.getModifierList();
      final String highestVisibility = isOriginal ?
                                       myNewVisibility :
                                       VisibilityUtil.getHighestVisibility(myNewVisibility, VisibilityUtil.getVisibilityModifier(modifierList));
      VisibilityUtil.setVisibility(modifierList, highestVisibility);
    }

    if (myChangeInfo.isNameChanged) {
      String newName = baseMethod == null ? myChangeInfo.newName :
                       RefactoringUtil.suggestNewOverriderName(method.getName(), baseMethod.getName(), myChangeInfo.newName);

      if (newName != null && !newName.equals(method.getName())) {
        final PsiIdentifier nameId = method.getNameIdentifier();
        assert nameId != null;
        nameId.replace(JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory().createIdentifier(newName));
      }
    }

    final PsiSubstitutor substitutor = baseMethod == null ? PsiSubstitutor.EMPTY : calculateSubstitutor(method, baseMethod);

    if (myChangeInfo.isReturnTypeChanged) {
      final PsiType returnType = substitutor.substitute(myChangeInfo.newTypeElement);
      // don't modify return type for non-Java overriders (EJB)
      if (method.getName().equals(myChangeInfo.newName)) {
        final PsiTypeElement typeElement = method.getReturnTypeElement();
        if (typeElement != null) {
          typeElement.replace(factory.createTypeElement(returnType));
        }
      }
    }

    PsiParameterList list = method.getParameterList();
    PsiParameter[] parameters = list.getParameters();

    PsiParameter[] newParms = new PsiParameter[myChangeInfo.newParms.length];
    for (int i = 0; i < newParms.length; i++) {
      ParameterInfoImpl info = myChangeInfo.newParms[i];
      int index = info.oldParameterIndex;
      if (index >= 0) {
        PsiParameter parameter = parameters[index];
        newParms[i] = parameter;

        String oldName = myChangeInfo.oldParameterNames[index];
        if (!oldName.equals(info.getName()) && oldName.equals(parameter.getName())) {
          PsiIdentifier newIdentifier = factory.createIdentifier(info.getName());
          parameter.getNameIdentifier().replace(newIdentifier);
        }

        String oldType = myChangeInfo.oldParameterTypes[index];
        if (!oldType.equals(info.getTypeText())) {
          parameter.normalizeDeclaration();
          PsiType newType = substitutor.substitute(info.createType(myChangeInfo.getMethod().getParameterList(), myManager));

          parameter.getTypeElement().replace(factory.createTypeElement(newType));
        }
      } else {
        newParms[i] = createNewParameter(info, substitutor);
      }
    }

    resolveParameterVsFieldsConflicts(newParms, method, list, myChangeInfo.toRemoveParm);
    fixJavadocsForChangedMethod(method);
    if (myChangeInfo.isExceptionSetOrOrderChanged) {
      final PsiClassType[] newExceptions = getPrimaryChangedExceptionInfo(myChangeInfo);
      fixPrimaryThrowsLists(method, newExceptions);
    }
  }

  private static void resolveParameterVsFieldsConflicts(final PsiParameter[] newParms,
                                                 final PsiMethod method,
                                                 final PsiParameterList list,
                                                 boolean[] toRemoveParm) throws IncorrectOperationException {
    List<FieldConflictsResolver> conflictResolvers = new ArrayList<FieldConflictsResolver>();
    for (PsiParameter parameter : newParms) {
      conflictResolvers.add(new FieldConflictsResolver(parameter.getName(), method.getBody()));
    }
    ChangeSignatureUtil.synchronizeList(list, Arrays.asList(newParms), ParameterList.INSTANCE, toRemoveParm);
    JavaCodeStyleManager.getInstance(list.getProject()).shortenClassReferences(list);
    for (FieldConflictsResolver fieldConflictsResolver : conflictResolvers) {
      fieldConflictsResolver.fix();
    }
  }

  private static PsiSubstitutor calculateSubstitutor(PsiMethod derivedMethod, PsiMethod baseMethod) {
    PsiSubstitutor substitutor;
    if (derivedMethod.getManager().areElementsEquivalent(derivedMethod, baseMethod)) {
      substitutor = PsiSubstitutor.EMPTY;
    } else {
      final PsiClass baseClass = baseMethod.getContainingClass();
      final PsiClass derivedClass = derivedMethod.getContainingClass();
      if(baseClass != null && derivedClass != null && InheritanceUtil.isInheritorOrSelf(derivedClass, baseClass, true)) {
        final PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, derivedClass, PsiSubstitutor.EMPTY);
        final MethodSignature superMethodSignature = baseMethod.getSignature(superClassSubstitutor);
        final MethodSignature methodSignature = derivedMethod.getSignature(PsiSubstitutor.EMPTY);
        final PsiSubstitutor superMethodSubstitutor = MethodSignatureUtil.getSuperMethodSignatureSubstitutor(methodSignature, superMethodSignature);
        substitutor = superMethodSubstitutor != null ? superMethodSubstitutor : superClassSubstitutor;
      } else {
        substitutor = PsiSubstitutor.EMPTY;
      }
    }
    return substitutor;
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

  private static class MyParameterUsageInfo extends UsageInfo {
    final String oldParameterName;
    final String newParameterName;

    public MyParameterUsageInfo(PsiElement element, String oldParameterName, String newParameterName) {
      super(element);
      this.oldParameterName = oldParameterName;
      this.newParameterName = newParameterName;
    }
  }

  private static class RenamedParameterCollidesWithLocalUsageInfo extends UnresolvableCollisionUsageInfo {
    private final PsiElement myCollidingElement;
    private final PsiMethod myMethod;

    public RenamedParameterCollidesWithLocalUsageInfo(PsiParameter parameter, PsiElement collidingElement, PsiMethod method) {
      super(parameter, collidingElement);
      myCollidingElement = collidingElement;
      myMethod = method;
    }

    public String getDescription() {
      return RefactoringBundle.message("there.is.already.a.0.in.the.1.it.will.conflict.with.the.renamed.parameter",
                                       RefactoringUIUtil.getDescription(myCollidingElement, true),
                                       RefactoringUIUtil.getDescription(myMethod, true));
    }
  }

  private void fixJavadocsForChangedMethod(PsiMethod method) throws IncorrectOperationException {
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final ParameterInfoImpl[] newParms = myChangeInfo.newParms;
    LOG.assertTrue(parameters.length == newParms.length);
    final Set<PsiParameter> newParameters = new HashSet<PsiParameter>();
    for (int i = 0; i < newParms.length; i++) {
      ParameterInfoImpl newParm = newParms[i];
      if (newParm.oldParameterIndex < 0 ||
          !newParm.getName().equals(myChangeInfo.oldParameterNames[newParm.oldParameterIndex])) {
        newParameters.add(parameters[i]);
      }
    }
    RefactoringUtil.fixJavadocsForParams(method, newParameters);
  }

  private static class ExpressionList implements ChangeSignatureUtil.ChildrenGenerator<PsiExpressionList, PsiExpression> {
    public static final ExpressionList INSTANCE = new ExpressionList();
    public List<PsiExpression> getChildren(PsiExpressionList psiExpressionList) {
      return Arrays.asList(psiExpressionList.getExpressions());
    }
  }

  private static class ParameterList implements ChangeSignatureUtil.ChildrenGenerator<PsiParameterList, PsiParameter> {
    public static final ParameterList INSTANCE = new ParameterList();
    public List<PsiParameter> getChildren(PsiParameterList psiParameterList) {
      return Arrays.asList(psiParameterList.getParameters());
    }
  }

  private static class ThrowsList implements ChangeSignatureUtil.ChildrenGenerator<PsiReferenceList, PsiJavaCodeReferenceElement> {
    public static final ThrowsList INSTANCE = new ThrowsList();
    public List<PsiJavaCodeReferenceElement> getChildren(PsiReferenceList throwsList) {
      return Arrays.asList(throwsList.getReferenceElements());
    }
  }

}
