package com.intellij.problems;

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.Disposable;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author cdr
 */
public abstract class WolfTheProblemSolver implements ProjectComponent {
  public static WolfTheProblemSolver getInstance(Project project) {
    return project.getComponent(WolfTheProblemSolver.class);
  }

  public abstract boolean isProblemFile(VirtualFile virtualFile);

  public abstract void weHaveGotProblem(Problem problem);
  public abstract void clearProblems(@NotNull VirtualFile virtualFile);

  public abstract boolean hasProblemFilesBeneath(ProjectViewNode scope);

  public abstract boolean hasProblemFilesBeneath(PsiElement scope);

  public abstract boolean hasProblemFilesBeneath(Module scope);

  public abstract Problem convertToProblem(CompilerMessage message);
  public abstract Problem convertToProblem(VirtualFile virtualFile, int line, int column, String[] message);

  public abstract void reportProblems(final VirtualFile file, Collection<Problem> problems);

  public abstract boolean hasSyntaxErrors(final VirtualFile file);

  public interface ProblemListener {
    void problemsChanged(Collection<VirtualFile> added, Collection<VirtualFile> removed);
  }

  public abstract void addProblemListener(ProblemListener listener);
  public abstract void addProblemListener(ProblemListener listener, Disposable parentDisposable);
  public abstract void removeProblemListener(ProblemListener listener);
}
