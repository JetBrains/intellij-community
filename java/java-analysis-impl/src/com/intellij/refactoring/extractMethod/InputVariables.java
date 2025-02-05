// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.extractMethod;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.VariableData;
import com.intellij.refactoring.util.duplicates.DuplicatesFinder;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class InputVariables {
  private final List<VariableData> myInputVariables;

  private final List<? extends PsiVariable> myInitialParameters;
  private final Project myProject;
  private final LocalSearchScope myScope;

  private final ParametersFolder myFolding;
  private boolean myFoldingAvailable;

  private final @NotNull Set<? extends PsiField> myUsedInstanceFields;
  private boolean myPassFields;

  public InputVariables(@NotNull List<? extends PsiVariable> inputVariables,
                        @NotNull Project project,
                        @NotNull LocalSearchScope scope,
                        boolean foldingAvailable,
                        @NotNull Set<? extends PsiField> usedInstanceFields) {
    myInitialParameters = inputVariables;
    myProject = project;
    myScope = scope;
    myFoldingAvailable = foldingAvailable;
    myFolding = new ParametersFolder();
    myUsedInstanceFields = usedInstanceFields;
    myInputVariables = wrapInputVariables(inputVariables);
  }

  /**
   * copy use only
   */
  private InputVariables(@NotNull List<? extends VariableData> inputVariableData,
                         @NotNull Project project,
                         @NotNull LocalSearchScope scope,
                         boolean foldingAvailable,
                         @NotNull ParametersFolder folding,
                         @NotNull List<? extends PsiVariable> initialParameters,
                         @NotNull Set<? extends PsiField> usedInstanceFields) {
    myInitialParameters = initialParameters;
    myProject = project;
    myScope = scope;
    myFoldingAvailable = foldingAvailable;
    myInputVariables = new ArrayList<>(inputVariableData);
    myFolding = folding;
    myUsedInstanceFields = usedInstanceFields;
  }

  public boolean isFoldable() {
    return myFolding.isFoldable();
  }

  public void setPassFields(boolean passFields) {
    if (!hasInstanceFields()) {
      return;
    }
    myPassFields = passFields;

    myInputVariables.clear();
    myInputVariables.addAll(wrapInputVariables(myInitialParameters));
  }

  public boolean isPassFields() {
    return myPassFields;
  }

  private @NotNull List<VariableData> wrapInputVariables(@NotNull List<? extends PsiVariable> inputVariables) {
    UniqueNameGenerator nameGenerator = new UniqueNameGenerator();
    List<VariableData> inputData = new ArrayList<>(inputVariables.size());
    for (PsiVariable var : inputVariables) {
      final String defaultName = getParameterName(var);
      String name = nameGenerator.generateUniqueName(defaultName);
      PsiType type = GenericsUtil.getVariableTypeByExpressionType(var.getType());
      final Map<PsiCodeBlock, PsiType> casts = new HashMap<>();
      for (PsiReference reference : ReferencesSearch.search(var, myScope).asIterable()) {
        final PsiElement element = reference.getElement();
        final PsiElement parent = element.getParent();
        final PsiCodeBlock block = PsiTreeUtil.getParentOfType(parent, PsiCodeBlock.class);
        if (parent instanceof PsiTypeCastExpression) {
          final PsiType currentType = casts.get(block);
          final PsiType castType = ((PsiTypeCastExpression)parent).getType();
          casts.put(block, casts.containsKey(block) && currentType == null ? null : getBroaderType(currentType, castType));
        }
        else {
          casts.put(block, null);
        }
      }
      if (!casts.containsValue(null)) {
        PsiType currentType = null;
        for (PsiType psiType : casts.values()) {
          currentType = getBroaderType(currentType, psiType);
          if (currentType == null) {
            break;
          }
        }
        if (currentType != null) {
          currentType = checkTopLevelInstanceOf(currentType, myScope);
          if (currentType != null) {
            type = currentType;
          }
        }
      }

      VariableData data = new VariableData(var, type);
      data.name = name;
      data.passAsParameter = true;
      inputData.add(data);

      if (myFoldingAvailable) {
        myFolding.isParameterFoldable(data, myScope, inputVariables, nameGenerator, defaultName);
      }
    }


    if (myFoldingAvailable) {
      final Set<VariableData> toDelete = new HashSet<>();
      for (int i = inputData.size() - 1; i >=0; i--) {
        final VariableData data = inputData.get(i);
        if (myFolding.isParameterSafeToDelete(data, myScope)) {
          toDelete.add(data);
        }
      }
      inputData.removeAll(toDelete);
    }


    if (myPassFields) {
      for (PsiField var : myUsedInstanceFields) {
        final VariableData data = new VariableData(var, var.getType());
        data.name = nameGenerator.generateUniqueName(getParameterName(var));
        data.passAsParameter = true;
        inputData.add(data);
      }
    }
    return inputData;
  }

  private @NotNull String getParameterName(@NotNull PsiVariable var) {
    if (var instanceof PsiParameter) {
      return ((PsiParameter)var).getName();
    }
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(myProject);
    VariableKind kind = codeStyleManager.getVariableKind(var);
    String name = codeStyleManager.variableNameToPropertyName(var.getName(), kind);
    name = codeStyleManager.propertyNameToVariableName(name, VariableKind.PARAMETER);
    return name;
  }

  private static @Nullable PsiType checkTopLevelInstanceOf(PsiType currentType, @NotNull LocalSearchScope localSearchScope) {
    final PsiElement[] scope = localSearchScope.getScope();
    if (scope.length == 1 && scope[0] instanceof PsiIfStatement) {
      final PsiExpression condition = ((PsiIfStatement)scope[0]).getCondition();
      if (condition != null) {
        PsiInstanceOfExpression[] expressions = PsiTreeUtil.getChildrenOfType(condition, PsiInstanceOfExpression.class);
        if (expressions != null) {
          for (PsiInstanceOfExpression instanceOfExpression : expressions) {
            if (isOfCurrentType(instanceOfExpression, currentType)) return null;
          }
        }
        else if (condition instanceof PsiInstanceOfExpression) {
          if (isOfCurrentType((PsiInstanceOfExpression)condition, currentType)) return null;
        }
      }
    }
    return currentType;
  }

  private static boolean isOfCurrentType(@NotNull PsiInstanceOfExpression expr, PsiType currentType) {
    final PsiTypeElement checkType = expr.getCheckType();
    return checkType != null && checkType.getType().equals(currentType);
  }

  private static @Nullable PsiType getBroaderType(PsiType currentType, PsiType castType) {
    if (currentType != null) {
      if (castType != null) {
        if (TypeConversionUtil.isAssignable(castType, currentType)) {
          return castType;
        }
        if (!TypeConversionUtil.isAssignable(currentType, castType)) {
          for (PsiType superType : castType.getSuperTypes()) {
            if (TypeConversionUtil.isAssignable(superType, currentType)) {
              return superType;
            }
          }
          return null;
        }
      }
    }
    else {
      return castType;
    }
    return currentType;
  }

  public @NotNull List<VariableData> getInputVariables() {
    return myInputVariables;
  }

  public PsiExpression replaceWrappedReferences(PsiElement @NotNull [] elements, PsiExpression expression) {
    if (!myFoldingAvailable) return expression;

    boolean update = elements[0] == expression;
    myFolding.foldParameterUsagesInBody(myInputVariables, elements, myScope);
    return update ? (PsiExpression)elements[0] : expression;
  }

  public boolean toDeclareInsideBody(@NotNull PsiVariable variable) {
    List<VariableData> knownVars = new ArrayList<>(myInputVariables);
    for (VariableData data : knownVars) {
      if (data.variable.equals(variable)) {
        return false;
      }
    }
    return !myFolding.wasExcluded(variable);
  }

  public boolean contains(@NotNull PsiVariable variable) {
    for (VariableData data : myInputVariables) {
      if (data.variable.equals(variable)) return true;
    }
    return false;
  }

  public void removeParametersUsedInExitsOnly(@NotNull PsiElement codeFragment,
                                              @NotNull Collection<? extends PsiStatement> exitStatements,
                                              @NotNull ControlFlow controlFlow,
                                              int startOffset,
                                              int endOffset) {
    final LocalSearchScope scope = new LocalSearchScope(codeFragment);
    Variables:
    for (Iterator<VariableData> iterator = myInputVariables.iterator(); iterator.hasNext();) {
      final VariableData data = iterator.next();
      for (PsiReference ref : ReferencesSearch.search(data.variable, scope).asIterable()) {
        PsiElement element = ref.getElement();
        int elementOffset = controlFlow.getStartOffset(element);
        if (elementOffset >= startOffset && elementOffset <= endOffset) {
          if (!isInExitStatements(element, exitStatements)) continue Variables;
        }
      }
      iterator.remove();
    }
  }

  private static boolean isInExitStatements(@NotNull PsiElement element, @NotNull Collection<? extends PsiStatement> exitStatements) {
    for (PsiStatement exitStatement : exitStatements) {
      if (PsiTreeUtil.isAncestor(exitStatement, element, false)) return true;
    }
    return false;
  }


  public @NotNull InputVariables copy() {
    return new InputVariables(myInputVariables, myProject, myScope, myFoldingAvailable, myFolding, myInitialParameters, Collections.emptySet());
  }

  public @NotNull InputVariables copyWithoutFolding() {
    return new InputVariables(myInitialParameters, myProject, myScope, false, Collections.emptySet());
  }

  public void appendCallArguments(@NotNull VariableData data, @NotNull StringBuilder buffer) {
    if (myFoldingAvailable) {
      buffer.append(myFolding.getGeneratedCallArgument(data));
    }
    else {
      if (!TypeConversionUtil.isAssignable(data.type, data.variable.getType())) {
        buffer.append("(").append(data.type.getCanonicalText()).append(")");
      }
      buffer.append(data.variable.getName());
    }
  }

  public @NotNull ParametersFolder getFolding() {
    return myFolding;
  }

  public void setFoldingAvailable(boolean foldingAvailable) {
    myFoldingAvailable = foldingAvailable;

    myFolding.clear();
    myInputVariables.clear();
    myInputVariables.addAll(wrapInputVariables(myInitialParameters));
  }

  public void annotateWithParameter(@NotNull PsiJavaCodeReferenceElement reference) {
    if (myInputVariables.isEmpty()) return;
    final PsiElement element = reference.resolve();
    for (VariableData data : myInputVariables) {
      if (data.variable.equals(element)) {
        PsiType type = data.variable.getType();
        final PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(reference, PsiMethodCallExpression.class);
        if (methodCallExpression != null) {
          int idx = ArrayUtil.find(methodCallExpression.getArgumentList().getExpressions(), reference);
          if (idx > -1) {
            final PsiMethod psiMethod = methodCallExpression.resolveMethod();
            if (psiMethod != null) {
              final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
              if (idx >= parameters.length) { //vararg parameter
                idx = parameters.length - 1;
                if (idx >= 0) { //incomplete code
                  type = parameters[idx].getType();
                }
              }
              if (type instanceof PsiEllipsisType) {
                type = ((PsiEllipsisType)type).getComponentType();
              }
            }
          }
        }
        if (!myFoldingAvailable || !myFolding.annotateWithParameter(data, reference)) {
          reference.putUserData(DuplicatesFinder.PARAMETER, new DuplicatesFinder.Parameter(data.variable, type));
        }
      }
    }
  }

  public void foldExtractedParameter(@NotNull PsiVariable extractedParameter, @NotNull PsiExpression value) {
    myFoldingAvailable = true;
    myFolding.putCallArgument(extractedParameter, value);
  }

  public boolean isFoldingSelectedByDefault() {
    return myFolding.isFoldingSelectedByDefault();
  }

  public boolean hasInstanceFields() {
    return !myUsedInstanceFields.isEmpty();
  }
}