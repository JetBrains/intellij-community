package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;

public class OptimizeImportsProcessor extends AbstractLayoutCodeProcessor {
  private static final String PROGRESS_TEXT = CodeInsightBundle.message("progress.text.optimizing.imports");
  private static final String COMMAND_NAME = CodeInsightBundle.message("process.optimize.imports");

  public OptimizeImportsProcessor(Project project) {
    super(project, COMMAND_NAME, PROGRESS_TEXT);
  }

  public OptimizeImportsProcessor(Project project, Module module) {
    super(project, module, COMMAND_NAME, PROGRESS_TEXT);
  }

  public OptimizeImportsProcessor(Project project, PsiDirectory directory, boolean includeSubdirs) {
    super(project, directory, includeSubdirs, PROGRESS_TEXT, COMMAND_NAME);
  }

  public OptimizeImportsProcessor(Project project, PsiFile file) {
    super(project, file, PROGRESS_TEXT, COMMAND_NAME);
  }

  public OptimizeImportsProcessor(Project project, PsiFile[] files, Runnable postRunnable) {
    super(project, files, PROGRESS_TEXT, COMMAND_NAME, postRunnable);
  }

  protected Runnable preprocessFile(final PsiFile file) throws IncorrectOperationException {
    final ImportOptimizer optimizer = file.getLanguage().getImportOptimizer();
    return optimizer != null ? optimizer.processFile(file) : EmptyRunnable.getInstance();
  }
}
