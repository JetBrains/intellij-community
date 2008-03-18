package com.intellij.codeInsight.navigation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;

public class GotoDeclarationAction extends BaseCodeInsightAction implements CodeInsightActionHandler {
  protected CodeInsightActionHandler getHandler() {
    return this;
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return true;
  }

  protected boolean isValidForLookup() {
    return true;
  }

  public void invoke(final Project project, Editor editor, PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    int offset = editor.getCaretModel().getOffset();
    PsiElement element = findTargetElement(project, editor, offset);
    if (element == null) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.goto.declaration");
      chooseAmbiguousTarget(editor, offset);
      return;
    }

    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.goto.declaration");
    PsiElement navElement = element.getNavigationElement();

    navElement = TargetElementUtilBase.getInstance().getGotoDeclarationTarget(element, navElement);

    if (navElement instanceof Navigatable) {
      if (((Navigatable)navElement).canNavigate()) {
        ((Navigatable)navElement).navigate(true);
      }
    }
    else if (navElement != null) {
      int navOffset = navElement.getTextOffset();
      VirtualFile virtualFile = PsiUtilBase.getVirtualFile(navElement);
      if (virtualFile != null) {
        new OpenFileDescriptor(project, virtualFile, navOffset).navigate(true);
      }
    }
  }

  private static void chooseAmbiguousTarget(final Editor editor, int offset) {
    PsiElementProcessor<PsiElement> navigateProcessor = new PsiElementProcessor<PsiElement>() {
      public boolean execute(final PsiElement element) {
        Navigatable navigatable = EditSourceUtil.getDescriptor(element);
        if (navigatable != null && navigatable.canNavigate()) {
          navigatable.navigate(true);
        }
        return true;
      }
    };
    chooseAmbiguousTarget(editor, offset,navigateProcessor, CodeInsightBundle.message("declaration.navigation.title"));
  }

  // returns true if processor is run or is going to be run after showing popup
  public static boolean chooseAmbiguousTarget(final Editor editor, int offset, PsiElementProcessor<PsiElement> processor,
                                              String titlePattern) {
    final PsiReference reference = TargetElementUtilBase.findReference(editor, offset);
    final Collection<PsiElement> candidates = suggestCandidates(reference);
    if (candidates.size() == 1) {
      PsiElement element = candidates.iterator().next();
      processor.execute(element);
      return true;
    }
    else if (candidates.size() > 1) {
      PsiElement[] elements = candidates.toArray(new PsiElement[candidates.size()]);
      final TextRange range = reference.getRangeInElement();
      final String refText = reference.getElement().getText().substring(range.getStartOffset(), range.getEndOffset());
      String title = MessageFormat.format(titlePattern, refText);
      NavigationUtil.getPsiElementPopup(elements, new DefaultPsiElementCellRenderer(), title, processor).showInBestPositionFor(editor);
      return true;
    }
    return false;
  }

  static Collection<PsiElement> suggestCandidates(final PsiReference reference) {
    if (reference == null) {
      return Collections.emptyList();
    }
    return TargetElementUtilBase.getInstance().getTargetCandidates(reference);
  }

  public boolean startInWriteAction() {
    return false;
  }

  @Nullable
  public static PsiElement findTargetElement(Project project, Editor editor, int offset) {
    int flags = TargetElementUtilBase.getInstance().getAllAccepted() & ~TargetElementUtilBase.ELEMENT_NAME_ACCEPTED;
    PsiElement element = TargetElementUtilBase.getInstance().findTargetElement(editor, flags, offset);

    if (element != null) return element;

    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) {
      return null;
    }
    PsiElement elementAt = file.findElementAt(offset);

    for(GotoDeclarationHandler handler: Extensions.getExtensions(GotoDeclarationHandler.EP_NAME)) {
      PsiElement result = handler.getGotoDeclarationTarget(elementAt);
      if (result != null) {
        return result;
      }
    }

    return null;
  }
}
