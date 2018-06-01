// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.google.common.collect.Lists;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RecentFilesSEContributor extends FileSearchEverywhereContributor {

  public RecentFilesSEContributor(Project project) {
    super(project);
  }

  @NotNull
  @Override
  public String getSearchProviderId() {
    return RecentFilesSEContributor.class.getSimpleName();
  }

  @NotNull
  @Override
  public String getGroupName() {
    return "Recent Files";
  }

  @Override
  public String includeNonProjectItemsText() {
    return null;
  }

  @Override
  public int getSortWeight() {
    return 70;
  }

  @Override
  public ContributorSearchResult<Object> search(String pattern, boolean everywhere, ProgressIndicator progressIndicator, int elementsLimit) {
    MinusculeMatcher matcher = NameUtil.buildMatcher("*" + pattern).build();
    List<VirtualFile> opened = Arrays.asList(FileEditorManager.getInstance(myProject).getSelectedFiles());
    List<VirtualFile> history = Lists.reverse(EditorHistoryManager.getInstance(myProject).getFileList());

    PsiManager psiManager = PsiManager.getInstance(myProject);
    Stream<VirtualFile> stream = history.stream();
    if (!StringUtil.isEmptyOrSpaces(pattern)) {
      stream = stream.filter(file -> matcher.matches(file.getName()));
    }
    List<Object> res = stream.filter(vf -> !opened.contains(vf) && vf.isValid())
                             .distinct()
                             .map(vf -> psiManager.findFile(vf))
                             .collect(Collectors.toList());

    return res.size() > elementsLimit
           ? new ContributorSearchResult<>(res.subList(0, elementsLimit), true)
           : new ContributorSearchResult<>(res);
  }
}
