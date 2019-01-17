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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NotNullFunction;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * refactored from {@link FieldCanBeLocalInspection}
 *
 * @author Danila Ponomarenko
 */
public abstract class BaseConvertToLocalQuickFix<V extends PsiVariable> implements LocalQuickFix {
  protected static final Logger LOG = Logger.getInstance(BaseConvertToLocalQuickFix.class);

  @Override
  @NotNull
  public final String getFamilyName() {
    return InspectionsBundle.message("inspection.convert.to.local.quickfix");
  }

  @Override
  public final void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final V variable = getVariable(descriptor);
    if (variable == null || !variable.isValid()) return; //weird. should not get here when field becomes invalid
    final PsiFile myFile = variable.getContainingFile();

    try {
      final List<PsiElement> newDeclarations = moveDeclaration(project, variable).stream()
        .map(declaration -> inlineRedundant(declaration))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

      if (newDeclarations.isEmpty()) return;

      positionCaretToDeclaration(project, myFile, newDeclarations.get(newDeclarations.size() - 1));
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Nullable
  private static PsiElement inlineRedundant(@Nullable PsiElement declaration) {
    if (declaration == null) return null;

    final PsiLocalVariable newVariable = extractDeclared(declaration);
    if (newVariable != null) {
      final PsiExpression initializer = ParenthesesUtils.stripParentheses(newVariable.getInitializer());

      if (VariableAccessUtils.localVariableIsCopy(newVariable, initializer)) {
        WriteAction.run(() -> {
          InlineUtil.inlineVariable(newVariable, initializer);
          declaration.delete();
        });
        return null;
      }
    }

    return declaration;
  }

  @Nullable
  private static PsiLocalVariable extractDeclared(@NotNull PsiElement declaration) {
    if (!(declaration instanceof PsiDeclarationStatement)) return null;

    final PsiElement[] declaredElements = ((PsiDeclarationStatement)declaration).getDeclaredElements();
    if (declaredElements.length != 1) return null;

    final PsiElement declared = declaredElements[0];

    if (!(declared instanceof PsiLocalVariable)) return null;

    return (PsiLocalVariable)declared;
  }

  @Nullable
  protected abstract V getVariable(@NotNull ProblemDescriptor descriptor);

  protected static void positionCaretToDeclaration(@NotNull Project project, @NotNull PsiFile psiFile, @NotNull PsiElement declaration) {
    final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor != null && (IJSwingUtilities.hasFocus(editor.getComponent()) || ApplicationManager.getApplication().isUnitTestMode())) {
      final PsiFile openedFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (openedFile == psiFile) {
        editor.getCaretModel().moveToOffset(declaration.getTextOffset());
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    }
  }

  protected void beforeDelete(@NotNull Project project, @NotNull V variable, @NotNull PsiElement newDeclaration) {
  }

  @NotNull
  protected List<PsiElement> moveDeclaration(@NotNull Project project, @NotNull V variable) {
    final Collection<PsiReference> references = ReferencesSearch.search(variable).findAll();
    if (references.isEmpty()) return Collections.emptyList();

    return Collections.singletonList(moveDeclaration(project, variable, references, true));
  }

  protected PsiElement moveDeclaration(Project project, V variable, final Collection<? extends PsiReference> references, boolean delete) {
    final PsiCodeBlock anchorBlock = findAnchorBlock(references);
    if (anchorBlock == null) return null; //was assert, but need to fix the case when obsolete inspection highlighting is left

    final PsiElement firstElement = getLowestOffsetElement(references);
    final String localName = suggestLocalName(project, variable, anchorBlock);

    final PsiElement anchor = getAnchorElement(anchorBlock, firstElement);


    final PsiAssignmentExpression anchorAssignmentExpression = searchAssignmentExpression(anchor);
    if (anchorAssignmentExpression != null && isVariableAssignment(anchorAssignmentExpression, variable)) {
      final Set<PsiReference> refsSet = new HashSet<>(references);
      refsSet.remove(anchorAssignmentExpression.getLExpression());
      return applyChanges(
        project,
        localName,
        anchorAssignmentExpression.getRExpression(),
        variable,
        refsSet,
        delete,
        declaration -> new CommentTracker().replaceAndRestoreComments(anchor, declaration)
      );
    }

    return applyChanges(
      project,
      localName,
      variable.getInitializer(),
      variable,
      references,
      delete,
      declaration -> {
        PsiElement parent = anchorBlock.getParent();
        if (parent instanceof PsiSwitchStatement) {
          PsiElement switchContainer = parent.getParent();
          return switchContainer.addBefore(declaration, parent);
        }
        return anchorBlock.addBefore(declaration, anchor);
      }
    );
  }

  protected PsiElement applyChanges(@NotNull final Project project,
                                    @NotNull final String localName,
                                    @Nullable final PsiExpression initializer,
                                    @NotNull final V variable,
                                    @NotNull final Collection<? extends PsiReference> references,
                                    final boolean delete, @NotNull final NotNullFunction<? super PsiDeclarationStatement, ? extends PsiElement> action) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);

    return WriteAction.compute(() -> {
      final PsiElement newDeclaration = moveDeclaration(elementFactory, localName, variable, initializer, action, references);
      if (delete) {
        deleteSourceVariable(project, variable, newDeclaration);
      }
      return newDeclaration;
    });
  }

  protected void deleteSourceVariable(@NotNull Project project, @NotNull V variable, PsiElement newDeclaration) {
    CommentTracker tracker = new CommentTracker();
    beforeDelete(project, variable, newDeclaration);
    variable.normalizeDeclaration();
    tracker.delete(variable);
    tracker.insertCommentsBefore(newDeclaration);
  }

  protected PsiElement moveDeclaration(PsiElementFactory elementFactory,
                                       String localName,
                                       V variable,
                                       PsiExpression initializer,
                                       NotNullFunction<? super PsiDeclarationStatement, ? extends PsiElement> action,
                                       Collection<? extends PsiReference> references) {
    final PsiDeclarationStatement declaration = elementFactory.createVariableDeclarationStatement(localName, variable.getType(), initializer);
    if (references.stream()
                  .map(PsiReference::getElement)
                  .anyMatch(element -> element instanceof PsiExpression && 
                                       PsiUtil.isAccessedForWriting((PsiExpression)element))) { 
      PsiUtil.setModifierProperty((PsiLocalVariable)declaration.getDeclaredElements()[0], PsiModifier.FINAL, false);
    }
    final PsiElement newDeclaration = action.fun(declaration);
    retargetReferences(elementFactory, localName, references);
    return newDeclaration;
  }

  @Nullable
  private static PsiAssignmentExpression searchAssignmentExpression(@Nullable PsiElement anchor) {
    if (!(anchor instanceof PsiExpressionStatement)) {
      return null;
    }

    final PsiExpression anchorExpression = ((PsiExpressionStatement)anchor).getExpression();

    if (!(anchorExpression instanceof PsiAssignmentExpression)) {
      return null;
    }

    return (PsiAssignmentExpression)anchorExpression;
  }

  private static boolean isVariableAssignment(@NotNull PsiAssignmentExpression expression, @NotNull PsiVariable variable) {
    if (expression.getOperationTokenType() != JavaTokenType.EQ) {
      return false;
    }

    if (!(expression.getLExpression() instanceof PsiReferenceExpression)) {
      return false;
    }

    final PsiReferenceExpression leftExpression = (PsiReferenceExpression)expression.getLExpression();

    return leftExpression.isReferenceTo(variable);
  }

  @NotNull
  protected abstract String suggestLocalName(@NotNull Project project, @NotNull V variable, @NotNull PsiCodeBlock scope);

  private static void retargetReferences(PsiElementFactory elementFactory, String localName, Collection<? extends PsiReference> refs)
    throws IncorrectOperationException {
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)elementFactory.createExpressionFromText(localName, null);
    for (PsiReference ref : refs) {
      if (ref instanceof PsiReferenceExpression) {
        ((PsiReferenceExpression)ref).replace(refExpr);
      }
    }
  }

  @Nullable
  private static PsiElement getAnchorElement(PsiCodeBlock anchorBlock, @NotNull PsiElement firstElement) {
    PsiElement element = firstElement;
    while (element != null && element.getParent() != anchorBlock) {
      element = element.getParent();
    }
    return element;
  }

  @Nullable
  private static PsiElement getLowestOffsetElement(@NotNull Collection<? extends PsiReference> refs) {
    PsiElement firstElement = null;
    for (PsiReference reference : refs) {
      final PsiElement element = reference.getElement();
      if (!(element instanceof PsiReferenceExpression)) continue;
      if (firstElement == null || firstElement.getTextRange().getStartOffset() > element.getTextRange().getStartOffset()) {
        firstElement = element;
      }
    }
    return firstElement;
  }

  private static PsiCodeBlock findAnchorBlock(final Collection<? extends PsiReference> refs) {
    PsiCodeBlock result = null;
    for (PsiReference psiReference : refs) {
      final PsiElement element = psiReference.getElement();
      PsiCodeBlock block = PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class);
      if (result == null || block == null) {
        result = block;
      }
      else {
        final PsiElement commonParent = PsiTreeUtil.findCommonParent(result, block);
        result = PsiTreeUtil.getParentOfType(commonParent, PsiCodeBlock.class, false);
      }
    }
    return result;
  }
}
