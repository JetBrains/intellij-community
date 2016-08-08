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

/*
 * User: anna
 * Date: 22-Jun-2009
 */
package com.intellij.refactoring.extractMethod;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
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
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class InputVariables {
  private final List<VariableData> myInputVariables;

  private List<? extends PsiVariable> myInitialParameters;
  private final Project myProject;
  private final LocalSearchScope myScope;

  private ParametersFolder myFolding;
  private boolean myFoldingAvailable;

  private Set<PsiField> myUsedInstanceFields;
  private boolean       myPassFields;

  public InputVariables(final List<? extends PsiVariable> inputVariables,
                        Project project,
                        LocalSearchScope scope,
                        boolean foldingAvailable) {
    myInitialParameters = inputVariables;
    myProject = project;
    myScope = scope;
    myFoldingAvailable = foldingAvailable;
    myFolding = new ParametersFolder();
    myInputVariables = wrapInputVariables(inputVariables);
  }

  /**
   * copy use only
   */
  public InputVariables(List<VariableData> inputVariables,
                        Project project,
                        LocalSearchScope scope) {
    myProject = project;
    myScope = scope;
    myInputVariables = new ArrayList<>(inputVariables);
  }

  public boolean isFoldable() {
    return myFolding.isFoldable();
  }

  public void setUsedInstanceFields(Set<PsiField> usedInstanceFields) {
    myUsedInstanceFields = usedInstanceFields;
  }

  public void setPassFields(boolean passFields) {
    if (myUsedInstanceFields == null || myUsedInstanceFields.isEmpty()) {
      return;
    }
    myPassFields = passFields;

    myInputVariables.clear();
    myInputVariables.addAll(wrapInputVariables(myInitialParameters));
  }

  public ArrayList<VariableData> wrapInputVariables(final List<? extends PsiVariable> inputVariables) {
    UniqueNameGenerator nameGenerator = new UniqueNameGenerator();
    final ArrayList<VariableData> inputData = new ArrayList<>(inputVariables.size());
    for (PsiVariable var : inputVariables) {
      String name = nameGenerator.generateUniqueName(getParameterName(var));
      PsiType type = var.getType();
      if (type instanceof PsiEllipsisType) {
        type = ((PsiEllipsisType)type).toArrayType();
      }
      final Map<PsiCodeBlock, PsiType> casts = new HashMap<>();
      for (PsiReference reference : ReferencesSearch.search(var, myScope)) {
        final PsiElement element = reference.getElement();
        final PsiElement parent = element.getParent();
        final PsiCodeBlock block = PsiTreeUtil.getParentOfType(parent, PsiCodeBlock.class);
        if (parent instanceof PsiTypeCastExpression) {
          final PsiType currentType = casts.get(block);
          final PsiType castType = ((PsiTypeCastExpression)parent).getType();
          casts.put(block, casts.containsKey(block) && currentType == null ? null : getBroaderType(currentType, castType));
        } else {
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
          currentType = checkTopLevelInstanceOf(currentType);
          if (currentType != null) {
            type = currentType;
          }
        }
      }

      VariableData data = new VariableData(var, type);
      data.name = name;
      data.passAsParameter = true;
      inputData.add(data);

      if (myFoldingAvailable) myFolding.isParameterFoldable(data, myScope, inputVariables);
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


    if (myPassFields && myUsedInstanceFields != null) {
      for (PsiField var : myUsedInstanceFields) {
        final VariableData data = new VariableData(var, var.getType());
        data.name = nameGenerator.generateUniqueName(getParameterName(var));
        data.passAsParameter = true;
        inputData.add(data);
      }
    }
    return inputData;
  }

  private String getParameterName(PsiVariable var) {
    String name = var.getName();
    if (!(var instanceof PsiParameter)) {
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(myProject);
      VariableKind kind = codeStyleManager.getVariableKind(var);
      name = codeStyleManager.variableNameToPropertyName(name, kind);
      name = codeStyleManager.propertyNameToVariableName(name, VariableKind.PARAMETER);
    }
    return name;
  }

  @Nullable
  private PsiType checkTopLevelInstanceOf(final PsiType currentType) {
    final PsiElement[] scope = myScope.getScope();
    if (scope.length == 1 && scope[0] instanceof PsiIfStatement) {
      final PsiExpression condition = ((PsiIfStatement)scope[0]).getCondition();
      if (condition != null) {
        class CheckInstanceOf {
          boolean check(PsiInstanceOfExpression expr) {
            final PsiTypeElement checkType = expr.getCheckType();
            return checkType == null || !checkType.getType().equals(currentType);
          }
        }
        CheckInstanceOf checker = new CheckInstanceOf();
        final PsiInstanceOfExpression[] expressions = PsiTreeUtil.getChildrenOfType(condition, PsiInstanceOfExpression.class);
        if (expressions != null) {
          for (PsiInstanceOfExpression instanceOfExpression : expressions) {
            if (!checker.check(instanceOfExpression)) return null;
          }
        } else if (condition instanceof PsiInstanceOfExpression) {
           if (!checker.check((PsiInstanceOfExpression)condition)) return null;
        }
      }
    }
    return currentType;
  }

  @Nullable
  private static PsiType getBroaderType(PsiType currentType, PsiType castType) {
    if (currentType != null) {
      if (castType != null) {
        if (TypeConversionUtil.isAssignable(castType, currentType)) {
          return castType;
        } else if (!TypeConversionUtil.isAssignable(currentType, castType)) {
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

  public List<VariableData> getInputVariables() {
    return myInputVariables;
  }

  public PsiExpression replaceWrappedReferences(PsiElement[] elements, PsiExpression expression) {
    if (!myFoldingAvailable) return expression;

    boolean update = elements[0] == expression;
    myFolding.foldParameterUsagesInBody(myInputVariables, elements, myScope);
    return update ? (PsiExpression)elements[0] : expression;
  }

  public boolean toDeclareInsideBody(PsiVariable variable) {
    final ArrayList<VariableData> knownVars = new ArrayList<>(myInputVariables);
    for (VariableData data : knownVars) {
      if (data.variable.equals(variable)) {
        return false;
      }
    }
    return !myFolding.wasExcluded(variable);
  }

  public boolean contains(PsiVariable variable) {
    for (VariableData data : myInputVariables) {
      if (data.variable.equals(variable)) return true;
    }
    return false;
  }

  public void removeParametersUsedInExitsOnly(PsiElement codeFragment,
                                              Collection<PsiStatement> exitStatements,
                                              ControlFlow controlFlow,
                                              int startOffset,
                                              int endOffset) {
    final LocalSearchScope scope = new LocalSearchScope(codeFragment);
    Variables:
    for (Iterator<VariableData> iterator = myInputVariables.iterator(); iterator.hasNext();) {
      final VariableData data = iterator.next();
      for (PsiReference ref : ReferencesSearch.search(data.variable, scope)) {
        PsiElement element = ref.getElement();
        int elementOffset = controlFlow.getStartOffset(element);
        if (elementOffset >= startOffset && elementOffset <= endOffset) {
          if (!isInExitStatements(element, exitStatements)) continue Variables;
        }
      }
      iterator.remove();
    }
  }

  private static boolean isInExitStatements(PsiElement element, Collection<PsiStatement> exitStatements) {
    for (PsiStatement exitStatement : exitStatements) {
      if (PsiTreeUtil.isAncestor(exitStatement, element, false)) return true;
    }
    return false;
  }


  public InputVariables copy() {
    final InputVariables inputVariables = new InputVariables(myInputVariables, myProject, myScope);
    inputVariables.myFoldingAvailable = myFoldingAvailable;
    inputVariables.myFolding = myFolding;
    inputVariables.myInitialParameters = myInitialParameters;
    return inputVariables;
  }


  public void appendCallArguments(VariableData data, StringBuilder buffer) {
    if (myFoldingAvailable) {
      buffer.append(myFolding.getGeneratedCallArgument(data));
    } else {
      if (!TypeConversionUtil.isAssignable(data.type, data.variable.getType())) {
        buffer.append("(").append(data.type.getCanonicalText()).append(")");
      }
      buffer.append(data.variable.getName());
    }
  }

  public ParametersFolder getFolding() {
    return myFolding;
  }

  public void setFoldingAvailable(boolean foldingAvailable) {
    myFoldingAvailable = foldingAvailable;

    myFolding.clear();
    myInputVariables.clear();
    myInputVariables.addAll(wrapInputVariables(myInitialParameters));
  }

  public void annotateWithParameter(PsiJavaCodeReferenceElement reference) {
    for (VariableData data : myInputVariables) {
      final PsiElement element = reference.resolve();
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
          reference.putUserData(DuplicatesFinder.PARAMETER, Pair.create(data.variable, type));
        }
      }
    }
  }

  public boolean isFoldingSelectedByDefault() {
    return myFolding.isFoldingSelectedByDefault();
  }

  public boolean hasInstanceFields() {
    return myUsedInstanceFields != null && !myUsedInstanceFields.isEmpty();
  }
}