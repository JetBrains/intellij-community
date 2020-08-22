// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInspection.HintAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

public class RenameWrongRefFix implements IntentionAction, HintAction {
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
    if (!myRefExpr.isValid() || !BaseIntentionAction.canModify(myRefExpr)) return false;
    PsiElement refName = myRefExpr.getReferenceNameElement();
    if (refName == null) return false;

    return !CreateFromUsageUtils.isValidReference(myRefExpr, myUnresolvedOnly);
  }

  private LookupElement @NotNull [] collectItems() {
    Set<LookupElement> items = new LinkedHashSet<>();
    boolean qualified = myRefExpr.getQualifierExpression() != null;

    if (!qualified && !(myRefExpr.getParent() instanceof PsiMethodCallExpression)) {
      PsiVariable[] vars = CreateFromUsageUtils.guessMatchingVariables(myRefExpr);
      for (PsiVariable var : vars) {
        items.add(createLookupElement(var, v-> v.getName()));
      }
    } else {
      class MyScopeProcessor implements PsiScopeProcessor {
        final ArrayList<PsiElement> myResult = new ArrayList<>();
        final boolean myFilterMethods;
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

      if (!ApplicationManager.getApplication().isUnitTestMode()) items.add(createLookupElement(myRefExpr, r -> r.getReferenceName()));
      MyScopeProcessor processor = new MyScopeProcessor(myRefExpr);
      myRefExpr.processVariants(processor);
      PsiElement[] variants = processor.getVariants();
      for (PsiElement variant : variants) {
        items.add(createLookupElement((PsiNamedElement)variant, v -> v.getName()));
      }
    }

    return items.toArray(LookupElement.EMPTY_ARRAY);
  }

  @NotNull
  private static <T extends PsiElement> LookupElementBuilder createLookupElement(T variant, Function<T, String> toPresentableElement) {
    return LookupElementBuilder.create(variant, toPresentableElement.apply(variant));
  }

  @Override
  public void invoke(@NotNull Project project, final Editor editor, PsiFile file) {
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

  @Override
  public boolean showHint(@NotNull Editor editor) {
    if (!Registry.is("editor.show.popup.for.unresolved.references", false)) {
      return false;
    }
    LookupElement[] items = collectItems();
    if (items.length == 0) return false;
    String hintText = ShowAutoImportPass.getMessage(items.length > 1, items[0].getLookupString());
    TextRange textRange = myRefExpr.getTextRange();
    HintManager.getInstance().showQuestionHint(editor, hintText, textRange.getStartOffset(), textRange.getEndOffset(),
                                               new RenameWrongRefQuestionAction(items, editor));
    return true;
  }

  private final class RenameWrongRefQuestionAction implements QuestionAction {
    private final LookupElement[] myItems;
    private final Editor myEditor;

    private RenameWrongRefQuestionAction(LookupElement[] items, Editor editor) {
      myItems = items;
      myEditor = editor;
    }

    @Override
    public boolean execute() {
      if (myItems.length == 1 && myRefExpr.getTextRange().contains(myEditor.getCaretModel().getOffset())) {
        doFix(myItems[0]);
        return true;
      }
      JBPopupFactory.getInstance()
        .createPopupChooserBuilder(Arrays.asList(myItems))
        .setTitle(QuickFixBundle.message("rename.reference"))
        .setRenderer(new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(JList<?> list,
                                                        Object value,
                                                        int index,
                                                        boolean isSelected,
                                                        boolean cellHasFocus) {
            Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof LookupElement) {
              setText(((LookupElement)value).getLookupString());
            }
            return component;
          }
        })
        .setItemChosenCallback(element -> {
          doFix(element);
        })
        .createPopup()
        .showInBestPositionFor(myEditor);
      return true;
    }

    private void doFix(LookupElement element) {
      Project project = myRefExpr.getProject();
      final PsiExpression referenceFromText = JavaPsiFacade.getElementFactory(project)
          .createExpressionFromText(element.getLookupString(), myRefExpr);
      final PsiReferenceExpression[] refs = CreateFromUsageUtils.collectExpressions(myRefExpr, PsiMember.class, PsiFile.class);
      WriteCommandAction.runWriteCommandAction(project, getText(), null, () -> {
        for (PsiReferenceExpression ref : refs) {
          ref.replace(referenceFromText);
        }
      }, myRefExpr.getContainingFile());
    }
  }
}
