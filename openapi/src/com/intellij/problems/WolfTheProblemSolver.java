package com.intellij.problems;

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;import java.awt.*;

/**
 * @author cdr
 */
public abstract class WolfTheProblemSolver implements ProjectComponent {
  public static final Color PROBLEM_COLOR = new Color(200,0,0);

  public static WolfTheProblemSolver getInstance(Project project) {
    return project.getComponent(WolfTheProblemSolver.class);
  }

  public abstract boolean isProblemFile(VirtualFile virtualFile);

  public abstract ProblemUpdateTransaction startUpdatingProblemsInScope(CompileScope compileScope);

  public abstract ProblemUpdateTransaction startUpdatingProblemsInScope(VirtualFile virtualFile);

  public abstract boolean hasProblemFilesBeneath(ProjectViewNode scope);

  public interface ProblemUpdateTransaction {
    void addProblem(Problem problem);

    void addProblem(CompilerMessage message);

    void commit();
  }

  public interface ProblemListener {
    void problemsChanged(Collection<VirtualFile> added, Collection<VirtualFile> removed);
  }

  public abstract void addProblemListener(ProblemListener listener);
  public abstract void removeProblemListener(ProblemListener listener);
}
