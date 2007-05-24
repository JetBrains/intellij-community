package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author cdr
 */
public abstract class ProgressableTextEditorHighlightingPass extends TextEditorHighlightingPass {
  private volatile boolean myFinished;
  private volatile long myProgessLimit = 0;
  private final AtomicLong myProgressCount = new AtomicLong();
  private final Icon myInProgressIcon;
  private final String myPresentableName;

  protected ProgressableTextEditorHighlightingPass(final Project project, @Nullable final Document document, final Icon inProgressIcon,
                                                   final String presentableName) {
    super(project, document);
    myInProgressIcon = inProgressIcon;
    myPresentableName = presentableName;
  }

  public final void doCollectInformation(final ProgressIndicator progress) {
    myFinished = false;
    collectInformationWithProgress(progress);
  }

  protected abstract void collectInformationWithProgress(final ProgressIndicator progress);

  public final void doApplyInformationToEditor() {
    myFinished = true;
    applyInformationWithProgress();
    DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
    ((DaemonCodeAnalyzerImpl)daemonCodeAnalyzer).getFileStatusMap().markFileUpToDate(myDocument, getId());
  }

  protected abstract void applyInformationWithProgress();

  /**
   * @return number in the [0..1] range;
   * <0 means progress is not available
   */
  public double getProgress() {
    if (myProgessLimit == 0) return -1;
    return (double)myProgressCount.get() / myProgessLimit;
  }

  public boolean isFinished() {
    return myFinished;
  }

  protected final Icon getInProgressIcon() {
    return myInProgressIcon;
  }

  protected final String getPresentableName() {
    return myPresentableName;
  }

  public void setProgressLimit(long limit) {
    myProgessLimit = limit;
  }

  public void advanceProgress(int progress) {
    myProgressCount.addAndGet(progress);
  }

  public static class EmptyPass extends ProgressableTextEditorHighlightingPass {
    public EmptyPass(final Project project, @Nullable final Document document, Icon icon, String text) {
      super(project, document, icon, text);
    }

    protected void collectInformationWithProgress(final ProgressIndicator progress) {

    }

    protected void applyInformationWithProgress() {

    }

    public boolean isFinished() {
      return true;
    }

    // always valid
    public double getProgress() {
      return 0;
    }
  }
}
