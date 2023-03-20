// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.VariableData;
import com.intellij.refactoring.util.duplicates.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public class JavaDuplicatesExtractMethodProcessor extends ExtractMethodProcessor {
  private static final Logger LOG = Logger.getInstance(JavaDuplicatesExtractMethodProcessor.class);

  // it's a dummy but it's required to select the target class
  private static final Consumer<ExtractMethodProcessor> USE_SNAPSHOT_TARGET_CLASS = processor -> {};

  public JavaDuplicatesExtractMethodProcessor(PsiElement @NotNull [] elements, @NotNull @NlsContexts.DialogTitle String refactoringName) {
    this(elements, null, refactoringName);
  }

  public JavaDuplicatesExtractMethodProcessor(PsiElement @NotNull [] elements, @Nullable Editor editor, @Nullable @NlsContexts.DialogTitle String refactoringName) {
    super(elements[0].getProject(), editor, elements, null, refactoringName, "", HelpID.EXTRACT_METHOD);
  }

  public void applyFrom(@NotNull ExtractMethodProcessor from, @NotNull Map<PsiVariable, PsiVariable> variablesMapping) {
    myMethodName = from.myMethodName != null ? from.myMethodName : "dummyMethodName";
    myStatic = from.myStatic;
    myIsChainedConstructor = from.myIsChainedConstructor;
    myMethodVisibility = from.myMethodVisibility;
    myNullability = from.myNullability;
    myReturnType = from.myReturnType;
    myOutputVariables = Arrays.stream(from.myOutputVariables)
      .map(variable -> variablesMapping.getOrDefault(variable, variable))
      .toArray(PsiVariable[]::new);
    myOutputVariable = ArrayUtil.getFirstElement(myOutputVariables);
    myArtificialOutputVariable = variablesMapping.getOrDefault(from.myArtificialOutputVariable, from.myArtificialOutputVariable);

    List<VariableData> variableDatum = new ArrayList<>();
    List<VariableData> inputVariables = getInputVariables().getInputVariables();
    for (int i = 0; i < from.myVariableDatum.length; i++) {
      VariableData fromData = from.myVariableDatum[i];
      PsiVariable mappedVariable = variablesMapping.get(fromData.variable);
      if (isReferenced(mappedVariable, fromData.variable) && isUnchanged(mappedVariable, fromData.type, inputVariables)) {
        VariableData newData = fromData.substitute(mappedVariable);
        variableDatum.add(newData);
      }
    }
    Set<PsiVariable> parameterVariables = ContainerUtil.map2Set(variableDatum, data -> data.variable);
    for (VariableData data : inputVariables) {
      if (!parameterVariables.contains(data.variable)) {
        variableDatum.add(data);
      }
    }
    myVariableDatum = variableDatum.toArray(new VariableData[0]);
  }

  private static boolean isUnchanged(PsiVariable fromVariable, PsiType fromType, @NotNull List<? extends VariableData> inputVariables) {
    for (VariableData data : inputVariables) {
      if (data.variable == fromVariable) {
        return data.type != null && data.type.equalsToText(fromType.getCanonicalText());
      }
    }
    return true;
  }

  public boolean prepareFromSnapshot(@NotNull ExtractMethodSnapshot from, boolean showErrorHint) {
    applyFromSnapshot(from);
    PsiFile psiFile = myElements[0].getContainingFile();
    ExtractMethodSnapshot.SNAPSHOT_KEY.set(psiFile, from);
    try {
      if (!prepare(USE_SNAPSHOT_TARGET_CLASS, showErrorHint)) {
        return false;
      }
    }
    finally {
      ExtractMethodSnapshot.SNAPSHOT_KEY.set(psiFile, null);
    }
    myStatic = from.myStatic;
    myInputVariables.setFoldingAvailable(from.myFoldable);
    return true;
  }

  private void applyFromSnapshot(@NotNull ExtractMethodSnapshot from) {
    myMethodName = from.myMethodName;
    myStatic = from.myStatic;
    myIsChainedConstructor = from.myIsChainedConstructor;
    myMethodVisibility = from.myMethodVisibility;
    myNullability = from.myNullability;
    myReturnType = from.myReturnType != null ? from.myReturnType.getType() : null;
    myOutputVariables = ContainerUtil.map2Array(from.myOutputVariables, new PsiVariable[0], SmartPsiElementPointer::getElement);
    LOG.assertTrue(!ArrayUtil.contains(null, myOutputVariables));

    myOutputVariable = ArrayUtil.getFirstElement(myOutputVariables);
    myArtificialOutputVariable = from.myArtificialOutputVariable != null ? from.myArtificialOutputVariable.getElement() : null;

    myVariableDatum = ContainerUtil.map2Array(from.myVariableDatum, new VariableData[0], VariableDataSnapshot::getData);
    LOG.assertTrue(!ArrayUtil.contains(null, myVariableDatum));
  }

  private boolean isReferenced(@Nullable PsiVariable variable, PsiVariable fromVariable) {
    return variable == fromVariable || // it's a freshlyDeclaredParameter
           variable != null && ReferencesSearch.search(variable, new LocalSearchScope(myElements)).findFirst() != null;
  }

  public void applyDefaults(@NotNull String methodName, @PsiModifier.ModifierConstant @NotNull String visibility) {
    myMethodName = methodName;
    myVariableDatum = getInputVariables().getInputVariables().toArray(new VariableData[0]);
    myMethodVisibility = visibility;

    myArtificialOutputVariable = PsiTypes.voidType().equals(myReturnType) ? getArtificialOutputVariable() : null;
    final PsiType returnType = myArtificialOutputVariable != null ? myArtificialOutputVariable.getType() : myReturnType;

    if (returnType != null) {
      myReturnType = returnType;
    }
  }

  @Override
  public void doExtract() {
    chooseAnchor();
    super.doExtract();
  }

  public void updateStaticModifier(List<Match> matches) {
    if (!isStatic() && isCanBeStatic()) {
      for (Match match : matches) {
        if (!isInSameFile(match) || !isInSameClass(match)) {
          PsiUtil.setModifierProperty(myExtractedMethod, PsiModifier.STATIC, true);
          myStatic = true;
          break;
        }
      }
    }
  }

  public void putExtractedParameters(Map<PsiLocalVariable, ExtractedParameter> extractedParameters) {
    for (Map.Entry<PsiLocalVariable, ExtractedParameter> entry : extractedParameters.entrySet()) {
      myInputVariables.foldExtractedParameter(entry.getKey(), entry.getValue().myPattern.getUsage());
    }
  }

  public boolean prepare(boolean showErrorHint) {
    return prepare(null, showErrorHint);
  }

  private boolean prepare(@Nullable Consumer<? super ExtractMethodProcessor> pass, boolean showErrorHint) {
    setShowErrorDialogs(false);
    try {
      if (prepare(pass)) {
        return true;
      }

      final String message = RefactoringBundle.getCannotRefactorMessage(
        RefactoringBundle.message("is.not.supported.in.the.current.context", myRefactoringName));
      LOG.info(message);
      if (showErrorHint) {
        CommonRefactoringUtil.showErrorHint(myProject, null, message, myRefactoringName, HelpID.EXTRACT_METHOD);
      }
      return false;
    }
    catch (PrepareFailedException e) {
      LOG.info(e);
      if (showErrorHint) {
        CommonRefactoringUtil.showErrorHint(myProject, null, e.getMessage(), myRefactoringName, HelpID.EXTRACT_METHOD);
      }
      return false;
    }
  }

  @Override
  public PsiElement processMatch(Match match) throws IncorrectOperationException {
    boolean inMultipleFiles = !isInSameFile(match);
    if (inMultipleFiles) {
      relaxMethodVisibility(match);
    }
    boolean inMultipleClasses = !isInSameClass(match);

    PsiElement element = super.processMatch(match);

    if (inMultipleFiles || inMultipleClasses) {
      PsiMethodCallExpression callExpression = getMatchMethodCallExpression(element);
      if (callExpression != null) {
        return updateCallQualifier(callExpression);
      }
    }
    return element;
  }

  @Override
  protected boolean isFoldingApplicable() {
    return false;
  }

  @NotNull
  private PsiElement updateCallQualifier(PsiMethodCallExpression callExpression) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(myProject);
    PsiClass psiClass = myExtractedMethod.getContainingClass();
    LOG.assertTrue(psiClass != null, "myExtractedMethod.getContainingClass");

    PsiReferenceExpression newQualifier = factory.createReferenceExpression(psiClass);
    callExpression.getMethodExpression().setQualifierExpression(newQualifier);
    return JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(callExpression);
  }

  @NotNull
  public DuplicatesFinder createDuplicatesFinder() {
    ReturnValue returnValue = myOutputVariables.length == 1 ? new VariableReturnValue(myOutputVariables[0]) : null;

    Set<PsiVariable> effectivelyLocal = getEffectivelyLocalVariables();
    return new DuplicatesFinder(myElements, myInputVariables, returnValue,
                                Collections.emptyList(), DuplicatesFinder.MatchType.PARAMETRIZED, effectivelyLocal, null);
  }

  private void relaxMethodVisibility(Match match) {
    if (isInSamePackage(match)) {
      PsiUtil.setModifierProperty(myExtractedMethod, PsiModifier.PRIVATE, false);
    }
    else {
      PsiUtil.setModifierProperty(myExtractedMethod, PsiModifier.PUBLIC, true);
    }
  }

  private boolean isInSameFile(Match match) {
    return myExtractedMethod.getContainingFile() == match.getMatchStart().getContainingFile();
  }

  private boolean isInSamePackage(Match match) {
    PsiFile psiFile = myExtractedMethod.getContainingFile();
    PsiFile matchFile = match.getMatchStart().getContainingFile();
    return psiFile instanceof PsiJavaFile &&
           matchFile instanceof PsiJavaFile &&
           Objects.equals(((PsiJavaFile)psiFile).getPackageName(), ((PsiJavaFile)matchFile).getPackageName());
  }

  private boolean isInSameClass(Match match) {
    PsiClass matchClass = PsiTreeUtil.getParentOfType(match.getMatchStart(), PsiClass.class);
    PsiClass psiClass = PsiTreeUtil.getParentOfType(myExtractedMethod, PsiClass.class);
    return matchClass != null && PsiTreeUtil.isAncestor(psiClass, matchClass, false);
  }
}
