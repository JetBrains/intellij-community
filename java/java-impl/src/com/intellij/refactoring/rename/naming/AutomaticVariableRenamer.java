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
package com.intellij.refactoring.rename.naming;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.search.JavaFunctionalExpressionSearcher;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.StdTokenSets;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author dsl
 */
public class AutomaticVariableRenamer extends AutomaticRenamer {
  private final Set<PsiNamedElement> myToUnpluralize = new HashSet<>();

  public AutomaticVariableRenamer(PsiClass aClass, String newClassName, Collection<UsageInfo> usages) {
    final String oldClassName = aClass.getName();
    final Set<PsiFile> files = new HashSet<>();
    for (final UsageInfo info : usages) {
      final PsiElement element = info.getElement();
      if (!(element instanceof PsiJavaCodeReferenceElement)) continue;
      files.add(element.getContainingFile());
      final PsiDeclarationStatement statement = PsiTreeUtil.getParentOfType(element, PsiDeclarationStatement.class);
      if (statement != null) {
        for(PsiElement declaredElement: statement.getDeclaredElements()) {
          if (declaredElement instanceof PsiVariable) {
            checkRenameVariable(element, (PsiVariable) declaredElement, oldClassName);
          }
        }
      }
      else {
        PsiVariable variable = PsiTreeUtil.getParentOfType(element, PsiVariable.class);
        if (variable != null) {
          checkRenameVariable(element, variable, oldClassName);
          if (variable instanceof PsiField) {
            for(PsiField field: getFieldsInSameDeclaration((PsiField) variable)) {
              checkRenameVariable(element, field, oldClassName);
            }
          }
        }
      }
    }

    if (files.size() < JavaFunctionalExpressionSearcher.SMART_SEARCH_THRESHOLD && oldClassName != null) {
      for (PsiFile file : files) {
        for (PsiLambdaExpression expression : SyntaxTraverser.psiTraverser().withRoot(file).filter(PsiLambdaExpression.class)) {
          final PsiParameter[] parameters = expression.getParameterList().getParameters();
          for (PsiParameter parameter : parameters) {
            if (aClass.equals(PsiUtil.resolveClassInType(parameter.getType()))) {
              final String parameterName = parameter.getName();
              if (parameterName != null && StringUtil.containsIgnoreCase(parameterName, oldClassName)) {
                myElements.add(parameter);
              }
            }
          }
        }
      }
    }

    suggestAllNames(oldClassName, newClassName);
  }

  private static List<PsiField> getFieldsInSameDeclaration(final PsiField variable) {
    List<PsiField> result = new ArrayList<>();
    ASTNode node = variable.getNode();
    if (node != null) {
      while (true) {
        ASTNode comma = TreeUtil.skipElements(node.getTreeNext(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
        if (comma == null || comma.getElementType() != JavaTokenType.COMMA) break;
        ASTNode nextField = TreeUtil.skipElements(comma.getTreeNext(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
        if (nextField == null || nextField.getElementType() != JavaElementType.FIELD) break;
        result.add((PsiField) nextField.getPsi());
        node = nextField;
      }
    }
    return result;
  }

  private void checkRenameVariable(final PsiElement element, final PsiVariable variable, final String oldClassName) {
    final PsiTypeElement typeElement = variable.getTypeElement();
    if (typeElement == null) return;
    final PsiJavaCodeReferenceElement ref = typeElement.getInnermostComponentReferenceElement();
    if (ref == null) return;
    final String variableName = variable.getName();
    if (variableName != null && !StringUtil.containsIgnoreCase(variableName, oldClassName)) return;
    if (ref.equals(element)) {
      myElements.add(variable);
      if (variable.getType() instanceof PsiArrayType) {
        myToUnpluralize.add(variable);
      }
    }
    else {
      PsiType collectionType = JavaPsiFacade.getInstance(variable.getProject()).getElementFactory()
        .createTypeByFQClassName("java.util.Collection", variable.getResolveScope());
      if (!collectionType.isAssignableFrom(variable.getType())) return;
      final PsiTypeElement[] typeParameterElements = ref.getParameterList().getTypeParameterElements();
      for (PsiTypeElement typeParameterElement : typeParameterElements) {
        final PsiJavaCodeReferenceElement parameterRef = typeParameterElement.getInnermostComponentReferenceElement();
        if (parameterRef != null && parameterRef.equals(element)) {
          myElements.add(variable);
          myToUnpluralize.add(variable);
          break;
        }
      }
    }
  }

  public String getDialogTitle() {
    return RefactoringBundle.message("rename.variables.title");
  }

  public String getDialogDescription() {
    return RefactoringBundle.message("rename.variables.with.the.following.names.to");
  }

  public String entityName() {
    return RefactoringBundle.message("entity.name.variable");
  }

  public String nameToCanonicalName(@NotNull String name, PsiNamedElement psiVariable) {
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(psiVariable.getProject());
    final String propertyName = codeStyleManager.variableNameToPropertyName(name, codeStyleManager.getVariableKind((PsiVariable)psiVariable));
    if (myToUnpluralize.contains(psiVariable)) {
      final String singular = StringUtil.unpluralize(propertyName);
      if (singular != null) return singular;
      myToUnpluralize.remove(psiVariable); // no need to pluralize since it was initially in singular form
    }
    return propertyName;
  }

  public String canonicalNameToName(String canonicalName, PsiNamedElement psiVariable) {
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(psiVariable.getProject());
    final String variableName =
      codeStyleManager.propertyNameToVariableName(canonicalName, codeStyleManager.getVariableKind((PsiVariable)psiVariable));
    if (myToUnpluralize.contains(psiVariable)) return StringUtil.pluralize(variableName);
    return variableName;
  }
}