package com.intellij.codeInsight.completion.methodChains.search;

import com.intellij.compilerOutputIndex.impl.MethodsUsageIndex;
import com.intellij.compilerOutputIndex.impl.UsageIndexValue;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.SortedSet;
import java.util.TreeSet;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class MethodChainsSearchService {
  private final static SortedSet EMPTY_SORTED_SET = new TreeSet();

  private final MethodsUsageIndex myMethodsUsageIndex;
  private final Project myProject;

  public MethodChainsSearchService(final Project project) {
    myMethodsUsageIndex = MethodsUsageIndex.getInstance(project);
    myProject = project;
  }

  public Project getProject() {
    return myProject;
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public SortedSet<UsageIndexValue> getMethods(final String targetQName) {
    final TreeSet<UsageIndexValue> value = myMethodsUsageIndex.getValues(targetQName);
    if (value != null) {
      return value;
    }
    return EMPTY_SORTED_SET;
  }

  public PsiManager getPsiManager() {
    return PsiManager.getInstance(getProject());
  }
}
