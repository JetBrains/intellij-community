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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 07.05.2002
 * Time: 11:17:31
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.introduceParameter;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.util.*;
import com.intellij.refactoring.util.occurences.ExpressionOccurenceManager;
import com.intellij.refactoring.util.occurences.LocalVariableOccurenceManager;
import com.intellij.refactoring.util.occurences.OccurenceManager;
import com.intellij.refactoring.util.usageInfo.DefaultConstructorImplicitUsageInfo;
import com.intellij.refactoring.util.usageInfo.NoConstructorClassUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Set;

public class IntroduceParameterProcessor extends BaseRefactoringProcessor implements IntroduceParameterData {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceParameter.IntroduceParameterProcessor");

  private final PsiMethod myMethodToReplaceIn;
  private final PsiMethod myMethodToSearchFor;
  private PsiExpression myParameterInitializer;
  private final PsiExpression myExpressionToSearch;
  private final PsiLocalVariable myLocalVariable;
  private final boolean myRemoveLocalVariable;
  private final String myParameterName;
  private final boolean myReplaceAllOccurences;

  private int myReplaceFieldsWithGetters;
  private final boolean myDeclareFinal;
  private final boolean myGenerateDelegate;
  private PsiType myForcedType;
  private final TIntArrayList myParametersToRemove;
  private final PsiManager myManager;

  /**
   * if expressionToSearch is null, search for localVariable
   */
  public IntroduceParameterProcessor(@NotNull Project project,
                                     PsiMethod methodToReplaceIn,
                                     @NotNull PsiMethod methodToSearchFor,
                                     PsiExpression parameterInitializer,
                                     PsiExpression expressionToSearch,
                                     PsiLocalVariable localVariable,
                                     boolean removeLocalVariable,
                                     String parameterName,
                                     boolean replaceAllOccurences,
                                     int replaceFieldsWithGetters,
                                     boolean declareFinal,
                                     boolean generateDelegate,
                                     PsiType forcedType,
                                     @NotNull TIntArrayList parametersToRemove) {
    super(project);

    myMethodToReplaceIn = methodToReplaceIn;
    myMethodToSearchFor = methodToSearchFor;
    myParameterInitializer = parameterInitializer;
    myExpressionToSearch = expressionToSearch;

    myLocalVariable = localVariable;
    myRemoveLocalVariable = removeLocalVariable;
    myParameterName = parameterName;
    myReplaceAllOccurences = replaceAllOccurences;
    myReplaceFieldsWithGetters = replaceFieldsWithGetters;
    myDeclareFinal = declareFinal;
    myGenerateDelegate = generateDelegate;
    myForcedType = forcedType;
    myManager = PsiManager.getInstance(project);

    myParametersToRemove = parametersToRemove;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new IntroduceParameterViewDescriptor(myMethodToSearchFor);
  }

  public PsiType getForcedType() {
    return myForcedType;
  }

  public void setForcedType(PsiType forcedType) {
    myForcedType = forcedType;
  }

  public int getReplaceFieldsWithGetters() {
    return myReplaceFieldsWithGetters;
  }

  public void setReplaceFieldsWithGetters(int replaceFieldsWithGetters) {
    myReplaceFieldsWithGetters = replaceFieldsWithGetters;
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();

    PsiMethod[] overridingMethods =
      OverridingMethodsSearch.search(myMethodToSearchFor, myMethodToSearchFor.getUseScope(), true).toArray(PsiMethod.EMPTY_ARRAY);
    for (PsiMethod overridingMethod : overridingMethods) {
      result.add(new UsageInfo(overridingMethod));
    }
    if (!myGenerateDelegate) {
      PsiReference[] refs =
        MethodReferencesSearch.search(myMethodToSearchFor, GlobalSearchScope.projectScope(myProject), true).toArray(PsiReference.EMPTY_ARRAY);


      for (PsiReference ref1 : refs) {
        PsiElement ref = ref1.getElement();
        if (ref instanceof PsiMethod && ((PsiMethod)ref).isConstructor()) {
          DefaultConstructorImplicitUsageInfo implicitUsageInfo =
            new DefaultConstructorImplicitUsageInfo((PsiMethod)ref, ((PsiMethod)ref).getContainingClass(), myMethodToSearchFor);
          result.add(implicitUsageInfo);
        }
        else if (ref instanceof PsiClass) {
          result.add(new NoConstructorClassUsageInfo((PsiClass)ref));
        }
        else if (!insideMethodToBeReplaced(ref)) {
          result.add(new ExternalUsageInfo(ref));
        }
        else {
          result.add(new ChangedMethodCallInfo(ref));
        }
      }
    }

    if (myReplaceAllOccurences) {
      final OccurenceManager occurenceManager;
      if (myLocalVariable == null) {
        occurenceManager = new ExpressionOccurenceManager(myExpressionToSearch, myMethodToReplaceIn, null);
      }
      else {
        occurenceManager = new LocalVariableOccurenceManager(myLocalVariable, null);
      }
      PsiElement[] exprs = occurenceManager.getOccurences();
      for (PsiElement expr : exprs) {
        result.add(new InternalUsageInfo(expr));
      }
    }
    else {
      if (myExpressionToSearch != null) {
        result.add(new InternalUsageInfo(myExpressionToSearch));
      }
    }

    final UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  private static class ReferencedElementsCollector extends JavaRecursiveElementWalkingVisitor {
    private final Set<PsiElement> myResult = new HashSet<PsiElement>();

    @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      final PsiElement element = reference.resolve();
      if (element != null) {
        myResult.add(element);
      }
    }
  }

  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usagesIn = refUsages.get();
    MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();

    AnySameNameVariables anySameNameVariables = new AnySameNameVariables();
    myMethodToReplaceIn.accept(anySameNameVariables);
    final Pair<PsiElement, String> conflictPair = anySameNameVariables.getConflict();
    if (conflictPair != null) {
      conflicts.putValue(conflictPair.first, conflictPair.second);
    }

    if (!myGenerateDelegate) {
      detectAccessibilityConflicts(usagesIn, conflicts);
    }

    if (myParameterInitializer != null && !myMethodToReplaceIn.hasModifierProperty(PsiModifier.PRIVATE)) {
      final AnySupers anySupers = new AnySupers();
      myParameterInitializer.accept(anySupers);
      if (anySupers.isResult()) {
        for (UsageInfo usageInfo : usagesIn) {
          if (!(usageInfo.getElement() instanceof PsiMethod) && !(usageInfo instanceof InternalUsageInfo)) {
            if (!PsiTreeUtil.isAncestor(myMethodToReplaceIn.getContainingClass(), usageInfo.getElement(), false)) {
              conflicts.putValue(myParameterInitializer, RefactoringBundle.message("parameter.initializer.contains.0.but.not.all.calls.to.method.are.in.its.class",
                                                      CommonRefactoringUtil.htmlEmphasize(PsiKeyword.SUPER)));
              break;
            }
          }
        }
      }
    }

    for (IntroduceParameterMethodUsagesProcessor processor : IntroduceParameterMethodUsagesProcessor.EP_NAME.getExtensions()) {
      processor.findConflicts(this, refUsages.get(), conflicts);
    }

    return showConflicts(conflicts, usagesIn);
  }

  private void detectAccessibilityConflicts(final UsageInfo[] usageArray, MultiMap<PsiElement, String> conflicts) {
    if (myParameterInitializer != null) {
      final ReferencedElementsCollector collector = new ReferencedElementsCollector();
      myParameterInitializer.accept(collector);
      final Set<PsiElement> result = collector.myResult;
      if (!result.isEmpty()) {
        for (final UsageInfo usageInfo : usageArray) {
          if (usageInfo instanceof ExternalUsageInfo && isMethodUsage(usageInfo)) {
            final PsiElement place = usageInfo.getElement();
            for (PsiElement element : result) {
              if (element instanceof PsiField && myReplaceFieldsWithGetters != IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE) { //check getter access instead
                final PsiClass psiClass = ((PsiField)element).getContainingClass();
                LOG.assertTrue(psiClass != null);
                final PsiMethod method = psiClass.findMethodBySignature(PropertyUtil.generateGetterPrototype((PsiField)element), true);
                if (method != null){
                  element = method;
                }
              }
              if (element instanceof PsiMember &&
                  !JavaPsiFacade.getInstance(myProject).getResolveHelper().isAccessible((PsiMember)element, place, null)) {
                String message =
                  RefactoringBundle.message(
                    "0.is.not.accessible.from.1.value.for.introduced.parameter.in.that.method.call.will.be.incorrect",
                    RefactoringUIUtil.getDescription(element, true),
                    RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(place), true));
                conflicts.putValue(element, message);
              }
            }
          }
        }
      }
    }
  }

  private static boolean isMethodUsage(UsageInfo usageInfo) {
    for (IntroduceParameterMethodUsagesProcessor processor : IntroduceParameterMethodUsagesProcessor.EP_NAME.getExtensions()) {
      if (processor.isMethodUsage(usageInfo)) return true;
    }
    return false;
  }

  public static class AnySupers extends JavaRecursiveElementWalkingVisitor {
    private boolean myResult = false;
    @Override public void visitSuperExpression(PsiSuperExpression expression) {
      myResult = true;
    }

    public boolean isResult() {
      return myResult;
    }

    @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitElement(expression);
    }
  }

  public class AnySameNameVariables extends JavaRecursiveElementWalkingVisitor {
    private Pair<PsiElement, String> conflict = null;

    public Pair<PsiElement, String> getConflict() {
      return conflict;
    }

    @Override public void visitVariable(PsiVariable variable) {
      if (variable == myLocalVariable) return;
      if (myParameterName.equals(variable.getName())) {
        String descr = RefactoringBundle.message("there.is.already.a.0.it.will.conflict.with.an.introduced.parameter",
                                                 RefactoringUIUtil.getDescription(variable, true));

        conflict = Pair.<PsiElement, String>create(variable, CommonRefactoringUtil.capitalize(descr));
      }
    }

    @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
    }

    @Override public void visitElement(PsiElement element) {
      if(conflict != null) return;
      super.visitElement(element);
    }
  }

  private boolean insideMethodToBeReplaced(PsiElement methodUsage) {
    PsiElement parent = methodUsage.getParent();
    while(parent != null) {
      if (parent.equals(myMethodToReplaceIn)) {
        return true;
      }
      parent = parent.getParent();
    }
    return false;
  }

  protected void refreshElements(PsiElement[] elements) {
  }

  protected void performRefactoring(UsageInfo[] usages) {
    try {
      PsiElementFactory factory = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory();
      PsiType initializerType = getInitializerType(myForcedType, myParameterInitializer, myLocalVariable);
      setForcedType(initializerType);

      // Converting myParameterInitializer
      if (myParameterInitializer == null) {
        LOG.assertTrue(myLocalVariable != null);
        myParameterInitializer = factory.createExpressionFromText(myLocalVariable.getName(), myLocalVariable);
      }
      else {
        myParameterInitializer = RefactoringUtil.convertInitializerToNormalExpression(myParameterInitializer, initializerType);
      }

      // Changing external occurences (the tricky part)
      
      for (UsageInfo usage : usages) {
        if (!(usage instanceof InternalUsageInfo)) {
          if (usage instanceof DefaultConstructorImplicitUsageInfo) {
            addSuperCall(usage, usages);
          }
          else if (usage instanceof NoConstructorClassUsageInfo) {
            addDefaultConstructor(usage, usages);
          }
          else {
            PsiElement element = usage.getElement();
            if (element instanceof PsiMethod) {
              if (!myManager.areElementsEquivalent(element, myMethodToReplaceIn)) {
                changeMethodSignatureAndResolveFieldConflicts(usage, usages);
              }
            }
            else if (!myGenerateDelegate) {
              changeExternalUsage(usage, usages);
            }
          }
        }
      }

      if (myGenerateDelegate) {
        generateDelegate();
      }

      // Changing signature of initial method
      // (signature of myMethodToReplaceIn will be either changed now or have already been changed)
      LOG.assertTrue(initializerType.isValid());
      final FieldConflictsResolver fieldConflictsResolver = new FieldConflictsResolver(myParameterName, myMethodToReplaceIn.getBody());
      changeMethodSignatureAndResolveFieldConflicts(new UsageInfo(myMethodToReplaceIn), usages);
      if (myMethodToSearchFor != myMethodToReplaceIn) {
        changeMethodSignatureAndResolveFieldConflicts(new UsageInfo(myMethodToSearchFor), usages);
      }
      ChangeContextUtil.clearContextInfo(myParameterInitializer);

      // Replacing expression occurences
      for (UsageInfo usage : usages) {
        if (usage instanceof ChangedMethodCallInfo) {
          PsiElement element = usage.getElement();

          processChangedMethodCall(element);
        }
        else if (usage instanceof InternalUsageInfo) {
          PsiElement element = usage.getElement();
          if (element instanceof PsiExpression) {
            element = RefactoringUtil.outermostParenthesizedExpression((PsiExpression)element);
          }
          if (element.getParent() instanceof PsiExpressionStatement) {
            element.getParent().delete();
          }
          else {
            PsiExpression newExpr = factory.createExpressionFromText(myParameterName, element);
            IntroduceVariableBase.replace((PsiExpression)element, newExpr, myProject);
          }
        }
      }

      if(myLocalVariable != null && myRemoveLocalVariable) {
        myLocalVariable.normalizeDeclaration();
        myLocalVariable.getParent().delete();
      }
      fieldConflictsResolver.fix();
    }
    catch (IncorrectOperationException ex) {
      LOG.error(ex);
    }
  }

  private void generateDelegate() throws IncorrectOperationException {
    final PsiMethod delegate = (PsiMethod)myMethodToReplaceIn.copy();
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory();
    ChangeSignatureProcessor.makeEmptyBody(elementFactory, delegate);
    final PsiCallExpression callExpression = ChangeSignatureProcessor.addDelegatingCallTemplate(delegate, delegate.getName());
    final PsiExpressionList argumentList = callExpression.getArgumentList();
    assert argumentList != null;
    final PsiParameter[] psiParameters = myMethodToReplaceIn.getParameterList().getParameters();

    final PsiParameter anchorParameter = getAnchorParameter(myMethodToReplaceIn);
    if (psiParameters.length == 0) {
      argumentList.add(myParameterInitializer);
    }
    else {
      for (int i = 0; i < psiParameters.length; i++) {
        PsiParameter psiParameter = psiParameters[i];
        if (!myParametersToRemove.contains(i)) {
          final PsiExpression expression = elementFactory.createExpressionFromText(psiParameter.getName(), delegate);
          argumentList.add(expression);
        }
        if (psiParameter == anchorParameter) {
          argumentList.add(myParameterInitializer);
        }
      }
    }

    myMethodToReplaceIn.getContainingClass().addBefore(delegate, myMethodToReplaceIn);
  }

  private void addDefaultConstructor(UsageInfo usage, UsageInfo[] usages) throws IncorrectOperationException {
    for (IntroduceParameterMethodUsagesProcessor processor : IntroduceParameterMethodUsagesProcessor.EP_NAME.getExtensions()) {
      if (!processor.processAddDefaultConstructor(this, usage, usages)) break;
    }
  }

  private void addSuperCall(UsageInfo usage, UsageInfo[] usages) throws IncorrectOperationException {
    for (IntroduceParameterMethodUsagesProcessor processor : IntroduceParameterMethodUsagesProcessor.EP_NAME.getExtensions()) {
      if (!processor.processAddSuperCall(this, usage, usages)) break;
    }
  }

  static PsiType getInitializerType(PsiType forcedType, PsiExpression parameterInitializer, PsiLocalVariable localVariable) {
    final PsiType initializerType;
    if (forcedType == null) {
      if (parameterInitializer == null) {
        if (localVariable != null) {
          initializerType = localVariable.getType();
        } else {
          LOG.assertTrue(false);
          initializerType = null;
        }
      } else {
        if (localVariable == null) {
          initializerType = RefactoringUtil.getTypeByExpressionWithExpectedType(parameterInitializer);
        } else {
          initializerType = localVariable.getType();
        }
      }
    } else {
      initializerType = forcedType;
    }
    return initializerType;
  }

  private void processChangedMethodCall(PsiElement element) throws IncorrectOperationException {
    if (element.getParent() instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression methodCall = (PsiMethodCallExpression)element.getParent();

      PsiElementFactory factory = JavaPsiFacade.getInstance(methodCall.getProject()).getElementFactory();
      PsiExpression expression = factory.createExpressionFromText(myParameterName, null);
      final PsiExpressionList argList = methodCall.getArgumentList();
      final PsiExpression[] exprs = argList.getExpressions();

      if (exprs.length > 0) {
        argList.addAfter(expression, exprs[exprs.length - 1]);
      }
      else {
        argList.add(expression);
      }

      removeParametersFromCall(argList);
    }
    else {
      LOG.error(element.getParent());
    }
  }

  private void removeParametersFromCall(final PsiExpressionList argList) {
    final PsiExpression[] exprs = argList.getExpressions();
    myParametersToRemove.forEachDescending(new TIntProcedure() {
      public boolean execute(final int paramNum) {
        try {
          exprs[paramNum].delete();
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
        return true;
      }
    });
  }

  private void changeExternalUsage(UsageInfo usage, UsageInfo[] usages) throws IncorrectOperationException {
    for (IntroduceParameterMethodUsagesProcessor processor: IntroduceParameterMethodUsagesProcessor.EP_NAME.getExtensions()) {
      if (!processor.processChangeMethodUsage(this, usage, usages)) break;
    }
  }

  protected String getCommandName() {
    return RefactoringBundle.message("introduce.parameter.command", UsageViewUtil.getDescriptiveName(myMethodToReplaceIn));
  }

  private void changeMethodSignatureAndResolveFieldConflicts(UsageInfo usage, UsageInfo[] usages) throws IncorrectOperationException {
    for (IntroduceParameterMethodUsagesProcessor processor : IntroduceParameterMethodUsagesProcessor.EP_NAME.getExtensions()) {
      if (!processor.processChangeMethodSignature(this, usage, usages)) break;
    }
  }

  @Nullable
  private static PsiParameter getAnchorParameter(PsiMethod methodToReplaceIn) {
    PsiParameterList parameterList = methodToReplaceIn.getParameterList();
    final PsiParameter anchorParameter;
    final PsiParameter[] parameters = parameterList.getParameters();
    final int length = parameters.length;
    if (!methodToReplaceIn.isVarArgs()) {
      anchorParameter = length > 0 ? parameters[length-1] : null;
    }
    else {
      LOG.assertTrue(length > 0);
      LOG.assertTrue(parameters[length-1].isVarArgs());
      anchorParameter = length > 1 ? parameters[length-2] : null;
    }
    return anchorParameter;
  }

  public PsiMethod getMethodToReplaceIn() {
    return myMethodToReplaceIn;
  }

  @NotNull
  public PsiMethod getMethodToSearchFor() {
    return myMethodToSearchFor;
  }

  public PsiExpression getParameterInitializer() {
    return myParameterInitializer;
  }

  public PsiExpression getExpressionToSearch() {
    return myExpressionToSearch;
  }

  public PsiLocalVariable getLocalVariable() {
    return myLocalVariable;
  }

  public boolean isRemoveLocalVariable() {
    return myRemoveLocalVariable;
  }

  @NotNull
  public String getParameterName() {
    return myParameterName;
  }

  public boolean isReplaceAllOccurences() {
    return myReplaceAllOccurences;
  }

  public boolean isDeclareFinal() {
    return myDeclareFinal;
  }

  public boolean isGenerateDelegate() {
    return myGenerateDelegate;
  }

  @NotNull
  public TIntArrayList getParametersToRemove() {
    return myParametersToRemove;
  }

  public PsiManager getManager() {
    return myManager;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

}
