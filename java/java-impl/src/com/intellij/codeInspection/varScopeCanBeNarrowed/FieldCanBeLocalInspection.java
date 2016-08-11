/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection.varScopeCanBeNarrowed;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.lang.java.JavaCommenter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;

/**
 * @author ven
 */
public class FieldCanBeLocalInspection extends FieldCanBeLocalInspectionBase {
  @Override
  protected LocalQuickFix createFix() {
    return new ConvertFieldToLocalQuickFix();
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    final JPanel listPanel = SpecialAnnotationsUtil
      .createSpecialAnnotationsListControl(EXCLUDE_ANNOS, InspectionsBundle.message("special.annotations.annotations.list"));

    final JPanel panel = new JPanel(new BorderLayout(2, 2));
    panel.add(new SingleCheckboxOptionsPanel("Ignore fields used in multiple methods", this, "IGNORE_FIELDS_USED_IN_MULTIPLE_METHODS"), BorderLayout.NORTH);
    panel.add(listPanel, BorderLayout.CENTER);
    return panel;
  }

  private static class ConvertFieldToLocalQuickFix extends BaseConvertToLocalQuickFix<PsiField> {
    @Nullable
    @Override
    protected PsiElement moveDeclaration(@NotNull final Project project, @NotNull final PsiField variable) {
      final Map<PsiCodeBlock, Collection<PsiReference>> refs = new HashMap<>();
      if (!groupByCodeBlocks(ReferencesSearch.search(variable).findAll(), refs)) return null;
      PsiElement element = null;
      for (Collection<PsiReference> psiReferences : refs.values()) {
        element = super.moveDeclaration(project, variable, psiReferences, false);
      }
      if (element != null) {
        final PsiElement finalElement = element;
        Runnable runnable = () -> {
          beforeDelete(project, variable, finalElement);
          variable.normalizeDeclaration();
          variable.delete();
        };
        ApplicationManager.getApplication().runWriteAction(runnable);
      }
      return element;
    }

    private static boolean groupByCodeBlocks(final Collection<PsiReference> allReferences, Map<PsiCodeBlock, Collection<PsiReference>> refs) {
      for (PsiReference psiReference : allReferences) {
        final PsiElement element = psiReference.getElement();
        final PsiCodeBlock block = PsiTreeUtil.getTopmostParentOfType(element, PsiCodeBlock.class);
        if (block == null) {
          return false;
        }
        
        Collection<PsiReference> references = refs.get(block);
        if (references == null) {
          references = new ArrayList<>();
          if (findExistentBlock(refs, psiReference, block, references)) continue;
          refs.put(block, references);
        }
        references.add(psiReference);
      }
      return true;
    }

    private static boolean findExistentBlock(Map<PsiCodeBlock, Collection<PsiReference>> refs,
                                             PsiReference psiReference,
                                             PsiCodeBlock block,
                                             Collection<PsiReference> references) {
      for (Iterator<PsiCodeBlock> iterator = refs.keySet().iterator(); iterator.hasNext(); ) {
        PsiCodeBlock codeBlock = iterator.next();
        if (PsiTreeUtil.isAncestor(codeBlock, block, false)) {
          refs.get(codeBlock).add(psiReference);
          return true;
        }
        else if (PsiTreeUtil.isAncestor(block, codeBlock, false)) {
          references.addAll(refs.get(codeBlock));
          iterator.remove();
          break;
        }
      }
      return false;
    }

    @Override
    @Nullable
    protected PsiField getVariable(@NotNull ProblemDescriptor descriptor) {
      return PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiField.class);
    }

    @Override
    protected void beforeDelete(@NotNull Project project, @NotNull PsiField variable, @NotNull PsiElement newDeclaration) {
      final PsiDocComment docComment = variable.getDocComment();
      if (docComment != null) moveDocCommentToDeclaration(project, docComment, newDeclaration);
    }

    @NotNull
    @Override
    protected String suggestLocalName(@NotNull Project project, @NotNull PsiField field, @NotNull PsiCodeBlock scope) {
      final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);

      final String propertyName = styleManager.variableNameToPropertyName(field.getName(), VariableKind.FIELD);
      final String localName = styleManager.propertyNameToVariableName(propertyName, VariableKind.LOCAL_VARIABLE);
      return RefactoringUtil.suggestUniqueVariableName(localName, scope, field);
    }

    private static void moveDocCommentToDeclaration(@NotNull Project project, @NotNull PsiDocComment docComment, @NotNull PsiElement declaration) {
      final StringBuilder buf = new StringBuilder();
      for (PsiElement psiElement : docComment.getDescriptionElements()) {
        buf.append(psiElement.getText());
      }
      if (buf.length() > 0) {
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
        final JavaCommenter commenter = new JavaCommenter();
        final PsiComment comment = elementFactory.createCommentFromText(commenter.getBlockCommentPrefix() + buf.toString() + commenter.getBlockCommentSuffix(), declaration);
        declaration.getParent().addBefore(comment, declaration);
      }
    }
  }
}