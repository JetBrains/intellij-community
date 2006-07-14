
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.EditorTracker;
import com.intellij.codeInsight.daemon.impl.FileStatusMap;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.psi.PsiFile;

public abstract class DaemonCodeAnalyzer implements ProjectComponent {
  public static DaemonCodeAnalyzer getInstance(Project project) {
    return project.getComponent(DaemonCodeAnalyzer.class);
  }

  public abstract void settingsChanged();

  public abstract void updateVisibleHighlighters(Editor editor);

  public abstract void setUpdateByTimerEnabled(boolean value);

  public abstract boolean isHighlightingAvailable(PsiFile file);

  public abstract void setImportHintsEnabled(PsiFile file, boolean value);
  public abstract void resetImportHintsEnabledForProject(); 
  public abstract void setHighlightingEnabled(PsiFile file, boolean value);
  public abstract boolean isImportHintsEnabled(PsiFile file);
  public abstract boolean isAutohintsAvailable(PsiFile file);

  /**
   * Force restart
   */ 
  public abstract void restart();

  public abstract EditorTracker getEditorTracker();

  public abstract FileStatusMap getFileStatusMap();

}