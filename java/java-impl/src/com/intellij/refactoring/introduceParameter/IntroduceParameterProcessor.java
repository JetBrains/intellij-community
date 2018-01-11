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
package com.intellij.refactoring.introduceParameter;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.util.*;
import com.intellij.refactoring.util.duplicates.MethodDuplicatesHandler;
import com.intellij.refactoring.util.occurrences.ExpressionOccurrenceManager;
import com.intellij.refactoring.util.occurrences.LocalVariableOccurrenceManager;
import com.intellij.refactoring.util.occurrences.OccurrenceManager;
import com.intellij.refactoring.util.usageInfo.DefaultConstructorImplicitUsageInfo;
import com.intellij.refactoring.util.usageInfo.NoConstructorClassUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

/**
 * @author dsl
 * @since 07.05.2002
 */
public class IntroduceParameterProcessor extends BaseRefactoringProcessor implements IntroduceParameterData {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceParameter.IntroduceParameterProcessor");

  private final PsiMethod myMethodToReplaceIn;
  private final PsiMethod myMethodToSearchFor;
  private PsiExpression myParameterInitializer;
  private final PsiExpression myExpressionToSearch;
  private final PsiLocalVariable myLocalVariable;
  private final boolean myRemoveLocalVariable;
  private final String myParameterName;
  private final boolean myReplaceAllOccurrences;

  private int myReplaceFieldsWithGetters;
  private final boolean myDeclareFinal;
  private final boolean myGenerateDelegate;
  private PsiType myForcedType;
  private final TIntArrayList myParametersToRemove;
  private final PsiManager myManager;
  private JavaExpressionWrapper myInitializerWrapper;
  private boolean myHasConflicts;

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
                                     boolean replaceAllOccurrences,
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
    myReplaceAllOccurrences = replaceAllOccurrences;
    myReplaceFieldsWithGetters = replaceFieldsWithGetters;
    myDeclareFinal = declareFinal;
    myGenerateDelegate = generateDelegate;
    myForcedType = forcedType;
    myManager = PsiManager.getInstance(project);

    myParametersToRemove = parametersToRemove;

    myInitializerWrapper = expressionToSearch == null ? null : new JavaExpressionWrapper(expressionToSearch);
  }

  public void setParameterInitializer(PsiExpression parameterInitializer) {
    myParameterInitializer = parameterInitializer;
  }

  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new IntroduceParameterViewDescriptor(myMethodToSearchFor);
  }

  @NotNull
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
    ArrayList<UsageInfo> result = new ArrayList<>();

    PsiMethod[] overridingMethods =
      OverridingMethodsSearch.search(myMethodToSearchFor).toArray(PsiMethod.EMPTY_ARRAY);
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
        else if (!IntroduceParameterUtil.insideMethodToBeReplaced(ref, myMethodToReplaceIn)) {
          result.add(new ExternalUsageInfo(ref));
        }
        else {
          result.add(new ChangedMethodCallInfo(ref));
        }
      }
    }

    if (myReplaceAllOccurrences) {
      for (PsiElement expr : getOccurrences()) {
        result.add(new InternalUsageInfo(expr));
      }
    }
    else {
      if (myExpressionToSearch != null && myExpressionToSearch.isValid()) {
        result.add(new InternalUsageInfo(myExpressionToSearch));
      }
    }

    final UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  protected PsiElement[] getOccurrences() {
    final OccurrenceManager occurrenceManager;
    if (myLocalVariable == null) {
      occurrenceManager = new ExpressionOccurrenceManager(myExpressionToSearch, myMethodToReplaceIn, null);
    }
    else {
      occurrenceManager = new LocalVariableOccurrenceManager(myLocalVariable, null);
    }
    return occurrenceManager.getOccurrences();
  }

  public boolean hasConflicts() {
    return myHasConflicts;
  }

  private static class ReferencedElementsCollector extends JavaRecursiveElementWalkingVisitor {
    private final Set<PsiElement> myResult = new HashSet<>();

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

  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usagesIn = refUsages.get();
    MultiMap<PsiElement, String> conflicts = new MultiMap<>();

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
              String message = RefactoringBundle.message("parameter.initializer.contains.0.but.not.all.calls.to.method.are.in.its.class",
                                                         CommonRefactoringUtil.htmlEmphasize(PsiKeyword.SUPER));
              conflicts.putValue(myParameterInitializer, message);
              break;
            }
          }
        }
      }
    }

    for (IntroduceParameterMethodUsagesProcessor processor : IntroduceParameterMethodUsagesProcessor.EP_NAME.getExtensions()) {
      processor.findConflicts(this, refUsages.get(), conflicts);
    }

    myHasConflicts = !conflicts.isEmpty();
    return showConflicts(conflicts, usagesIn);
  }

  private void detectAccessibilityConflicts(final UsageInfo[] usageArray, MultiMap<PsiElement, String> conflicts) {
    if (myParameterInitializer != null) {
      final ReferencedElementsCollector collector = new ReferencedElementsCollector();
      myParameterInitializer.accept(collector);
      final Set<PsiElement> result = collector.myResult;
      if (!result.isEmpty()) {
        for (final UsageInfo usageInfo : usageArray) {
          if (usageInfo instanceof ExternalUsageInfo && IntroduceParameterUtil.isMethodUsage(usageInfo)) {
            final PsiElement place = usageInfo.getElement();
            for (PsiElement element : result) {
              if (element instanceof PsiField && myReplaceFieldsWithGetters != IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE) {
                //check getter access instead
                final PsiClass psiClass = ((PsiField)element).getContainingClass();
                LOG.assertTrue(psiClass != null);
                final PsiMethod method = psiClass.findMethodBySignature(GenerateMembersUtil.generateGetterPrototype((PsiField)element), true);
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

  public static class AnySupers extends JavaRecursiveElementWalkingVisitor {
    private boolean myResult;
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
    private Pair<PsiElement, String> conflict;

    public Pair<PsiElement, String> getConflict() {
      return conflict;
    }

    @Override public void visitVariable(PsiVariable variable) {
      if (variable == myLocalVariable) return;
      if (variable instanceof PsiParameter && ((PsiParameter)variable).getDeclarationScope() == myMethodToReplaceIn) {
        if (getParametersToRemove().contains(myMethodToReplaceIn.getParameterList().getParameterIndex((PsiParameter)variable))){
          return;
        }
      }
      if (myParameterName.equals(variable.getName())) {
        String descr = RefactoringBundle.message("there.is.already.a.0.it.will.conflict.with.an.introduced.parameter",
                                                 RefactoringUIUtil.getDescription(variable, true));

        conflict = Pair.create(variable, CommonRefactoringUtil.capitalize(descr));
      }
    }

    @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
    }

    @Override public void visitElement(PsiElement element) {
      if(conflict != null) return;
      super.visitElement(element);
    }
  }

  @Nullable
  @Override
  protected String getRefactoringId() {
    return "refactoring.introduceParameter";
  }

  @Nullable
  @Override
  protected RefactoringEventData getBeforeData() {
    RefactoringEventData data = new RefactoringEventData();
    data.addElements(new PsiElement[] {myLocalVariable, myExpressionToSearch});
    return data;
  }

  @Nullable
  @Override
  protected RefactoringEventData getAfterData(@NotNull UsageInfo[] usages) {
    final PsiParameter parameter = JavaIntroduceParameterMethodUsagesProcessor.getAnchorParameter(myMethodToReplaceIn);
    final RefactoringEventData afterData = new RefactoringEventData();
    afterData.addElement(parameter);
    return afterData;
  }

  protected void performRefactoring(@NotNull UsageInfo[] usages) {
    try {
      PsiElementFactory factory = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory();
      PsiType initializerType = getInitializerType(myForcedType, myParameterInitializer, myLocalVariable);
      setForcedType(initializerType);

      // Converting myParameterInitializer
      if (myParameterInitializer == null) {
        LOG.assertTrue(myLocalVariable != null);
        myParameterInitializer = factory.createExpressionFromText(myLocalVariable.getName(), myLocalVariable);
      }
      else if (myParameterInitializer instanceof PsiArrayInitializerExpression){
        final PsiExpression newExprArrayInitializer =
          RefactoringUtil.createNewExpressionFromArrayInitializer((PsiArrayInitializerExpression)myParameterInitializer, initializerType);
        myParameterInitializer = (PsiExpression)myParameterInitializer.replace(newExprArrayInitializer);
      }

      myInitializerWrapper = new JavaExpressionWrapper(myParameterInitializer);

      // Changing external occurrences (the tricky part)

      IntroduceParameterUtil.processUsages(usages, this);

      if (myGenerateDelegate) {
        generateDelegate(myMethodToReplaceIn);
        if (myMethodToReplaceIn != myMethodToSearchFor) {
          final PsiMethod method = generateDelegate(myMethodToSearchFor);
          if (method.getContainingClass().isInterface()) {
            final PsiCodeBlock block = method.getBody();
            if (block != null) {
              block.delete();
            }
          }
        }
      }

      // Changing signature of initial method
      // (signature of myMethodToReplaceIn will be either changed now or have already been changed)
      LOG.assertTrue(initializerType.isValid());
      final FieldConflictsResolver fieldConflictsResolver = new FieldConflictsResolver(myParameterName, myMethodToReplaceIn.getBody());
      IntroduceParameterUtil.changeMethodSignatureAndResolveFieldConflicts(new UsageInfo(myMethodToReplaceIn), usages, this);
      if (myMethodToSearchFor != myMethodToReplaceIn) {
        IntroduceParameterUtil.changeMethodSignatureAndResolveFieldConflicts(new UsageInfo(myMethodToSearchFor), usages, this);
      }
      else if (myGenerateDelegate && myMethodToReplaceIn.findSuperMethods().length == 0) {
        final PsiAnnotation annotation = AnnotationUtil.findAnnotation(myMethodToReplaceIn, true, Override.class.getName());
        if (annotation != null) {
          annotation.delete();
        }
      }
      ChangeContextUtil.clearContextInfo(myParameterInitializer);

      // Replacing expression occurrences
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
          if (element != null) {
            if (element.getParent() instanceof PsiExpressionStatement) {
              element.getParent().delete();
            }
            else {
              PsiExpression newExpr = factory.createExpressionFromText(myParameterName, element);
              IntroduceVariableBase.replace((PsiExpression)element, newExpr, myProject);
            }
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

    if (isReplaceDuplicates()) {
      ApplicationManager.getApplication().invokeLater(() -> processMethodsDuplicates(), myProject.getDisposed());
    }
  }

  protected boolean isReplaceDuplicates() {
    return true;
  }

  private void processMethodsDuplicates() {
    final Runnable runnable = () -> {
      if (!myMethodToReplaceIn.isValid()) return;
      MethodDuplicatesHandler.invokeOnScope(myProject, Collections.singleton(myMethodToReplaceIn),
                                            new AnalysisScope(myMethodToReplaceIn.getContainingFile()), true);
    };
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ApplicationManager.getApplication().runReadAction(runnable), "Search method duplicates...", true, myProject);
  }

  private PsiMethod generateDelegate(final PsiMethod methodToReplaceIn) throws IncorrectOperationException {
    final PsiMethod delegate = (PsiMethod)methodToReplaceIn.copy();
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory();
    ChangeSignatureProcessor.makeEmptyBody(elementFactory, delegate);
    final PsiCallExpression callExpression = ChangeSignatureProcessor.addDelegatingCallTemplate(delegate, delegate.getName());
    final PsiExpressionList argumentList = callExpression.getArgumentList();
    assert argumentList != null;
    final PsiParameter[] psiParameters = methodToReplaceIn.getParameterList().getParameters();

    final PsiParameter anchorParameter = getAnchorParameter(methodToReplaceIn);
    if (psiParameters.length == 0) {
      argumentList.add(myParameterInitializer);
    }
    else {
      if (anchorParameter == null) {
        argumentList.add(myParameterInitializer);
      }
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

    return (PsiMethod)methodToReplaceIn.getContainingClass().addBefore(delegate, methodToReplaceIn);
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

      if (myMethodToReplaceIn == myMethodToSearchFor && PsiTreeUtil.isAncestor(methodCall, myParameterInitializer, false)) return;

      PsiElementFactory factory = JavaPsiFacade.getInstance(methodCall.getProject()).getElementFactory();
      PsiExpression expression = factory.createExpressionFromText(myParameterName, null);
      final PsiExpressionList argList = methodCall.getArgumentList();
      final PsiExpression[] exprs = argList.getExpressions();

      boolean first = false;
      PsiElement anchor = null;
      if (myMethodToSearchFor.isVarArgs()) {
        final int oldParamCount = myMethodToSearchFor.getParameterList().getParametersCount() - 1;
        if (exprs.length >= oldParamCount) {
          if (oldParamCount > 1) {
            anchor = exprs[oldParamCount - 2];
          }
          else {
            first = true;
            anchor = null;
          }
        } else {
          anchor = exprs[exprs.length -1];
        }
      } else if (exprs.length > 0) {
        anchor = exprs[exprs.length - 1];
      }

      if (anchor != null) {
        argList.addAfter(expression, anchor);
      }
      else {
        if (first && exprs.length > 0) {
          argList.addBefore(expression, exprs[0]);
        } else {
          argList.add(expression);
        }
      }

      removeParametersFromCall(argList);
    }
    else {
      LOG.error(element.getParent());
    }
  }

  private void removeParametersFromCall(final PsiExpressionList argList) {
    final PsiExpression[] exprs = argList.getExpressions();
    myParametersToRemove.forEachDescending(paramNum -> {
      if (paramNum < exprs.length) {
        try {
          exprs[paramNum].delete();
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
      return true;
    });
  }

  @NotNull
  protected String getCommandName() {
    return RefactoringBundle.message("introduce.parameter.command", DescriptiveNameUtil.getDescriptiveName(myMethodToReplaceIn));
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

  public JavaExpressionWrapper getParameterInitializer() {
    return myInitializerWrapper;
  }

  @NotNull
  public String getParameterName() {
    return myParameterName;
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

  @NotNull
  public Project getProject() {
    return myProject;
  }

}
