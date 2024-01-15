// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.fix;

import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.dataFlow.TrackingRunner;
import com.intellij.java.JavaBundle;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.LangBundle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class FindDfaProblemCauseFix extends ModCommandQuickFix implements LowPriorityAction {
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
  public boolean availableInBatchMode() {
    return false;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("quickfix.family.find.cause");
  }

  @Override
  public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiExpression element = myAnchor.getElement();
    if (element == null) return ModCommand.nop();
    TrackingRunner.CauseItem item = TrackingRunner.findProblemCause(myIgnoreAssertStatements, element, myProblemType);
    PsiFile file = element.getContainingFile();
    return displayProblemCause(file, item);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    return new IntentionPreviewInfo.Html(JavaBundle.message("quickfix.find.cause.description"));
  }

  static class CauseWithDepth {
    final int myDepth;
    final TrackingRunner.CauseItem myCauseItem;
    final CauseWithDepth myParent;
    final Document myDocument;

    CauseWithDepth(CauseWithDepth parent, TrackingRunner.CauseItem item, Document document) {
      myParent = parent;
      myDepth = parent == null ? 0 : parent.myDepth + 1;
      myCauseItem = item;
      myDocument = document;
    }

    @Override
    public @Nls String toString() {
      return StringUtil.repeat("  ", myDepth - 1) + myCauseItem.render(myDocument, myParent == null ? null : myParent.myCauseItem);
    }
  }

  private static @NotNull ModCommand displayProblemCause(@NotNull PsiFile file, @Nullable TrackingRunner.CauseItem root) {
    Project project = file.getProject();
    PsiFile topLevelFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(file);
    Document document = topLevelFile.getFileDocument();
    List<CauseWithDepth> causes;
    if (root == null) {
      causes = Collections.emptyList();
    } else {
      causes = StreamEx.ofTree(new CauseWithDepth(null, root, document), cwd -> cwd.myCauseItem.children()
        .map(child -> new CauseWithDepth(cwd, child, document))).skip(1).toList();
    }
    if (causes.isEmpty()) {
      return ModCommand.error(JavaAnalysisBundle.message("dfa.find.cause.unable"));
    }
    return ModCommand.chooseAction(StringUtil.wordsToBeginFromUpperCase(root.toString()),
                                   ContainerUtil.map(causes, NavigateToCauseAction::new));
  }
  
  private static final class NavigateToCauseAction implements ModCommandAction {
    private final @NotNull CauseWithDepth myCauseWithDepth;
    
    NavigateToCauseAction(@NotNull CauseWithDepth causeWithDepth) {
      myCauseWithDepth = causeWithDepth;
    }

    @Override
    public @NotNull String getFamilyName() {
      return LangBundle.message("command.name.navigate");
    }

    @Override
    public @NotNull Presentation getPresentation(@NotNull ActionContext context) {
      Presentation presentation = Presentation.of(myCauseWithDepth.toString());
      Segment segment = myCauseWithDepth.myCauseItem.getTargetSegment();
      if (segment != null) {
        presentation = presentation.withHighlighting(TextRange.create(segment));
      }
      return presentation;
    }

    @Override
    public @NotNull ModCommand perform(@NotNull ActionContext context) {
      TrackingRunner.CauseItem item = myCauseWithDepth.myCauseItem;
      Segment range = item.getTargetSegment();
      if (range == null) return ModCommand.nop();
      PsiFile targetFile = item.getFile();
      if (targetFile == null) return ModCommand.nop();
      VirtualFile virtualFile = targetFile.getVirtualFile();
      return new ModNavigate(virtualFile, range.getStartOffset(), range.getStartOffset(), range.getStartOffset())
        .andThen(ModCommand.info(StringUtil.capitalize(item.toString())));
    }
  }
}
