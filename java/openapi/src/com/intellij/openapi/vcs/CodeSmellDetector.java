package com.intellij.openapi.vcs;

import com.intellij.codeInsight.CodeSmellInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ServiceManager;

import java.util.List;

/**
 * @author yole
 */
public abstract class CodeSmellDetector {
  public static CodeSmellDetector getInstance(Project project) {
    return ServiceManager.getService(project, CodeSmellDetector.class);
  }

  /**
   * Performs pre-checkin code analysis on the specified files.
   *
   * @param files the files to analyze.
   * @return the list of problems found during the analysis.
   * @throws com.intellij.openapi.progress.ProcessCanceledException if the analysis was cancelled by the user.
   * @since 5.1
   */
  public abstract List<CodeSmellInfo> findCodeSmells(List<VirtualFile> files) throws ProcessCanceledException;

  /**
   * Shows the specified list of problems found during pre-checkin code analysis in a Messages pane.
   *
   * @param smells the problems to show.
   * @since 5.1
   */
  public abstract void showCodeSmellErrors(final List<CodeSmellInfo> smells);

}