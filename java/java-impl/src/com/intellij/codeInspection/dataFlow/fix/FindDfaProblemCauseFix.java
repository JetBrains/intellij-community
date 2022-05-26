// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.fix;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.unwrap.ScopeHighlighter;
import com.intellij.codeInspection.OnTheFlyLocalFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.dataFlow.TrackingRunner;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.java.JavaBundle;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class FindDfaProblemCauseFix implements OnTheFlyLocalFix, LowPriorityAction {
  private final boolean myIgnoreAssertStatements;
  private final SmartPsiElementPointer<PsiExpression> myAnchor;
  private final TrackingRunner.DfaProblemType myProblemType;

  public FindDfaProblemCauseFix(boolean ignoreAssertStatements,
                                PsiExpression anchor,
                                TrackingRunner.DfaProblemType problemType) {
    myIgnoreAssertStatements = ignoreAssertStatements;
    myAnchor = SmartPointerManager.createPointer(anchor);
    myProblemType = problemType;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("quickfix.family.find.cause");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    ThrowableComputable<TrackingRunner.CauseItem, RuntimeException> causeFinder = () -> {
      PsiExpression element = myAnchor.getElement();
      if (element == null) return null;
      return TrackingRunner.findProblemCause(myIgnoreAssertStatements, element, myProblemType);
    };
    TrackingRunner.CauseItem item = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ReadAction.compute(causeFinder), JavaBundle.message("progress.title.finding.cause"), true, project);
    PsiFile file = myAnchor.getContainingFile();
    if (item != null && file != null) {
      displayProblemCause(file, item);
    }
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    return new IntentionPreviewInfo.Html(JavaBundle.message("quickfix.find.cause.description"));
  }

  private static void displayProblemCause(PsiFile file, TrackingRunner.CauseItem root) {
    Project project = file.getProject();
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor == null) return;
    Document document = editor.getDocument();
    PsiFile topLevelFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(file);
    if (topLevelFile == null || document != topLevelFile.getViewProvider().getDocument()) return;
    class CauseWithDepth {
      final int myDepth;
      final TrackingRunner.CauseItem myCauseItem;
      final CauseWithDepth myParent;

      CauseWithDepth(CauseWithDepth parent, TrackingRunner.CauseItem item) {
        myParent = parent;
        myDepth = parent == null ? 0 : parent.myDepth + 1;
        myCauseItem = item;
      }

      @Override
      public String toString() {
        return StringUtil.repeat("  ", myDepth - 1) + myCauseItem.render(document, myParent == null ? null : myParent.myCauseItem);
      }
    }
    List<CauseWithDepth> causes;
    if (root == null) {
      causes = Collections.emptyList();
    } else {
      causes = StreamEx.ofTree(new CauseWithDepth(null, root), cwd -> cwd.myCauseItem.children()
        .map(child -> new CauseWithDepth(cwd, child))).skip(1).toList();
    }
    if (causes.isEmpty()) {
      HintManagerImpl hintManager = (HintManagerImpl)HintManager.getInstance();
      hintManager.showErrorHint(editor, JavaAnalysisBundle.message("dfa.find.cause.unable"));
      return;
    }
    if (causes.size() == 1) {
      TrackingRunner.CauseItem item = causes.get(0).myCauseItem;
      navigate(editor, file, item);
      return;
    }
    AtomicReference<ScopeHighlighter> highlighter = new AtomicReference<>(new ScopeHighlighter(editor));
    JBPopup popup = JBPopupFactory.getInstance().createPopupChooserBuilder(causes)
      .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      .setAccessibleName(root.toString())
      .setTitle(StringUtil.wordsToBeginFromUpperCase(root.toString()))
      .setMovable(false)
      .setResizable(false)
      .setRequestFocus(true)
      .setItemSelectedCallback((cause) -> {
        ScopeHighlighter h = highlighter.get();
        if (h == null) return;
        h.dropHighlight();
        if (cause == null) return;
        Segment target = cause.myCauseItem.getTargetSegment();
        if (target == null) return;
        TextRange range = TextRange.create(target);
        h.highlight(Pair.create(range, Collections.singletonList(range)));
      })
      .addListener(new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          highlighter.getAndSet(null).dropHighlight();
        }
      })
      .setItemChosenCallback(cause -> navigate(editor, file, cause.myCauseItem))
      .createPopup();
    popup.showInBestPositionFor(editor);
  }

  private static void navigate(Editor editor, PsiFile file, TrackingRunner.CauseItem item) {
    Segment range = item.getTargetSegment();
    if (range == null) return;
    PsiFile targetFile = item.getFile();
    assert targetFile == file;
    PsiNavigationSupport.getInstance().createNavigatable(file.getProject(), targetFile.getVirtualFile(), range.getStartOffset())
      .navigate(true);
    HintManagerImpl hintManager = (HintManagerImpl)HintManager.getInstance();
    hintManager.showInformationHint(editor, StringUtil.escapeXmlEntities(StringUtil.capitalize(item.toString())));
  }
}
