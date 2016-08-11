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
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 20, 2002
 * Time: 8:49:24 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public class RenameWrongRefFix implements IntentionAction {
  private final PsiReferenceExpression myRefExpr;
  @NonNls private static final String INPUT_VARIABLE_NAME = "INPUTVAR";
  @NonNls private static final String OTHER_VARIABLE_NAME = "OTHERVAR";
  private final boolean myUnresolvedOnly;

  public RenameWrongRefFix(@NotNull PsiReferenceExpression refExpr) {
    this(refExpr, false);
  }

  public RenameWrongRefFix(@NotNull PsiReferenceExpression refExpr, final boolean unresolvedOnly) {
    myRefExpr = refExpr;
    myUnresolvedOnly = unresolvedOnly;
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("rename.wrong.reference.text");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("rename.wrong.reference.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!myRefExpr.isValid() || !myRefExpr.getManager().isInProject(myRefExpr)) return false;
    int offset = editor.getCaretModel().getOffset();
    PsiElement refName = myRefExpr.getReferenceNameElement();
    if (refName == null) return false;
    TextRange textRange = refName.getTextRange();
    if (textRange == null || offset < textRange.getStartOffset() ||
        offset > textRange.getEndOffset()) {
      return false;
    }

    return !CreateFromUsageUtils.isValidReference(myRefExpr, myUnresolvedOnly);
  }

  private LookupElement[] collectItems() {
    Set<LookupElement> items = new LinkedHashSet<>();
    boolean qualified = myRefExpr.getQualifierExpression() != null;

    if (!qualified && !(myRefExpr.getParent() instanceof PsiMethodCallExpression)) {
      PsiVariable[] vars = CreateFromUsageUtils.guessMatchingVariables(myRefExpr);
      for (PsiVariable var : vars) {
        items.add(LookupElementBuilder.create(var.getName()));
      }
    } else {
      class MyScopeProcessor extends BaseScopeProcessor {
        ArrayList<PsiElement> myResult = new ArrayList<>();
        boolean myFilterMethods;
        boolean myFilterStatics;

        MyScopeProcessor(PsiReferenceExpression refExpression) {
          myFilterMethods = refExpression.getParent() instanceof PsiMethodCallExpression;
          PsiExpression qualifier = refExpression.getQualifierExpression();
          if (qualifier instanceof PsiReferenceExpression) {
            PsiElement e = ((PsiReferenceExpression) qualifier).resolve();
            myFilterStatics = e instanceof PsiClass;
          } else if (qualifier == null) {
            PsiModifierListOwner scope = PsiTreeUtil.getParentOfType(refExpression, PsiModifierListOwner.class);
            myFilterStatics = scope != null && scope.hasModifierProperty(PsiModifier.STATIC);
          }
        }

        @Override
        public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
          if (element instanceof PsiNamedElement
              && element instanceof PsiModifierListOwner
              && myFilterMethods == element instanceof PsiMethod) {
            if (((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC) == myFilterStatics) {
              myResult.add(element);
            }
          }
          return true;
        }

        public PsiElement[] getVariants () {
          return PsiUtilCore.toPsiElementArray(myResult);
        }
      }

      items.add(LookupElementBuilder.create(myRefExpr.getReferenceName()));
      MyScopeProcessor processor = new MyScopeProcessor(myRefExpr);
      myRefExpr.processVariants(processor);
      PsiElement[] variants = processor.getVariants();
      for (PsiElement variant : variants) {
        items.add(LookupElementBuilder.create(((PsiNamedElement)variant).getName()));
      }
    }

    return items.toArray(new LookupElement[items.size()]);
  }

  @Override
  public void invoke(@NotNull Project project, final Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    PsiReferenceExpression[] refs = CreateFromUsageUtils.collectExpressions(myRefExpr, PsiMember.class, PsiFile.class);
    PsiElement element = PsiTreeUtil.getParentOfType(myRefExpr, PsiMember.class, PsiFile.class);
    LookupElement[] items = collectItems();
    ReferenceNameExpression refExpr = new ReferenceNameExpression(items, myRefExpr.getReferenceName());

    TemplateBuilderImpl builder = new TemplateBuilderImpl(element);
    for (PsiReferenceExpression expr : refs) {
      if (!expr.equals(myRefExpr)) {
        builder.replaceElement(expr.getReferenceNameElement(), OTHER_VARIABLE_NAME, INPUT_VARIABLE_NAME, false);
      }
      else {
        builder.replaceElement(expr.getReferenceNameElement(), INPUT_VARIABLE_NAME, refExpr, true);
      }
    }

    final float proportion = EditorUtil.calcVerticalScrollProportion(editor);
    editor.getCaretModel().moveToOffset(element.getTextRange().getStartOffset());

    /*for (int i = refs.length - 1; i >= 0; i--) {
      TextRange range = refs[i].getReferenceNameElement().getTextRange();
      document.deleteString(range.getStartOffset(), range.getEndOffset());
    }
*/
    Template template = builder.buildInlineTemplate();
    editor.getCaretModel().moveToOffset(element.getTextRange().getStartOffset());

    TemplateManager.getInstance(project).startTemplate(editor, template);

    EditorUtil.setVerticalScrollProportion(editor, proportion);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
