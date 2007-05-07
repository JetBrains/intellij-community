/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 20, 2002
 * Time: 8:49:24 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class RenameWrongRefFix implements IntentionAction {
  private PsiReferenceExpression myRefExpr;
  @NonNls private static final String INPUT_VARIABLE_NAME = "INPUTVAR";
  @NonNls private static final String OTHER_VARIABLE_NAME = "OTHERVAR";

  public RenameWrongRefFix(PsiReferenceExpression refExpr) {
    myRefExpr = refExpr;
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("rename.wrong.reference.text");
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("rename.wrong.reference.family");
  }

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

    return !CreateFromUsageUtils.isValidReference(myRefExpr, false);
  }

  private class ReferenceNameExpression implements Expression {
    class HammingComparator implements Comparator<LookupItem> {
      public int compare(LookupItem lookupItem1, LookupItem lookupItem2) {
        String s1 = lookupItem1.getLookupString();
        String s2 = lookupItem2.getLookupString();
        int diff1 = 0;
        for (int i = 0; i < Math.min(s1.length(), myOldReferenceName.length()); i++) {
          if (s1.charAt(i) != myOldReferenceName.charAt(i)) diff1++;
        }
        int diff2 = 0;
        for (int i = 0; i < Math.min(s2.length(), myOldReferenceName.length()); i++) {
          if (s2.charAt(i) != myOldReferenceName.charAt(i)) diff2++;
        }
        return diff1 - diff2;
      }
    }

    ReferenceNameExpression(LookupItem[] items, String oldReferenceName) {
      myItems = items;
      myOldReferenceName = oldReferenceName;
      Arrays.sort(myItems, new HammingComparator ());
    }

    LookupItem[] myItems;
    private final String myOldReferenceName;

    public Result calculateResult(ExpressionContext context) {
      if (myItems == null || myItems.length == 0) {
        return new TextResult(myOldReferenceName);
      }
      return new TextResult(myItems[0].getLookupString());
    }

    public Result calculateQuickResult(ExpressionContext context) {
      return null;
    }

    public LookupItem[] calculateLookupItems(ExpressionContext context) {
      if (myItems == null || myItems.length == 1) return null;
      return myItems;
    }
  }

  private LookupItem[] collectItems() {
    Set<LookupItem> items = new LinkedHashSet<LookupItem>();
    boolean qualified = myRefExpr.getQualifierExpression() != null;

    if (!qualified && !(myRefExpr.getParent() instanceof PsiMethodCallExpression)) {
      PsiVariable[] vars = CreateFromUsageUtils.guessMatchingVariables(myRefExpr);
      for (PsiVariable var : vars) {
        LookupItemUtil.addLookupItem(items, var.getName(), "");
      }
    } else {
      class MyScopeProcessor extends BaseScopeProcessor {
        ArrayList<PsiElement> myResult = new ArrayList<PsiElement>();
        boolean myFilterMethods;
        boolean myFilterStatics = false;

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

        public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
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
          return myResult.toArray(new PsiElement[myResult.size()]);
        }
      }

      MyScopeProcessor processor = new MyScopeProcessor(myRefExpr);
      myRefExpr.processVariants(processor);
      PsiElement[] variants = processor.getVariants();
      for (PsiElement variant : variants) {
        LookupItemUtil.addLookupItem(items, ((PsiNamedElement)variant).getName(), "");
      }
    }

    return items.toArray(new LookupItem[items.size()]);
  }

  public void invoke(@NotNull Project project, final Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    PsiReferenceExpression[] refs = CreateFromUsageUtils.collectExpressions(myRefExpr, PsiMember.class, PsiFile.class);
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      PsiElement element = PsiTreeUtil.getParentOfType(myRefExpr, PsiMember.class, PsiFile.class);
      LookupItem[] items = collectItems();
      ReferenceNameExpression refExpr = new ReferenceNameExpression(items, myRefExpr.getReferenceName());

      TemplateBuilder builder = new TemplateBuilder(element);
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
  }

  public boolean startInWriteAction() {
    return true;
  }
}
