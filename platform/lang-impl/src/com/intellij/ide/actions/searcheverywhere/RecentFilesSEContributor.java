// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.google.common.collect.Lists;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RecentFilesSEContributor extends FileSearchEverywhereContributor {

  public RecentFilesSEContributor(@Nullable Project project, @Nullable PsiElement context) {
    super(project, context);
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
  public int getElementPriority(@NotNull Object element, @NotNull String searchPattern) {
    return super.getElementPriority(element, searchPattern) + 1;
  }

  @Override
  public void fetchElements(@NotNull String pattern, boolean everywhere, @Nullable SearchEverywhereContributorFilter<FileType> filter,
                            @NotNull ProgressIndicator progressIndicator, @NotNull Function<Object, Boolean> consumer) {
    if (myProject == null) {
      return; //nothing to search
    }

    String searchString = filterControlSymbols(pattern);
    MinusculeMatcher matcher = NameUtil.buildMatcher("*" + searchString).build();
    List<VirtualFile> opened = Arrays.asList(FileEditorManager.getInstance(myProject).getSelectedFiles());
    List<VirtualFile> history = Lists.reverse(EditorHistoryManager.getInstance(myProject).getFileList());

    List<Object> res = new ArrayList<>();
    ProgressIndicatorUtils.yieldToPendingWriteActions();
    ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(
      () -> {
        PsiManager psiManager = PsiManager.getInstance(myProject);
        Stream<VirtualFile> stream = history.stream();
        if (!StringUtil.isEmptyOrSpaces(searchString)) {
          stream = stream.filter(file -> matcher.matches(file.getName()));
        }
        res.addAll(stream.filter(vf -> !opened.contains(vf) && vf.isValid())
                     .distinct()
                     .map(vf -> psiManager.findFile(vf))
                     .filter(file -> file != null)
                     .collect(Collectors.toList())
        );

        for (Object element : res) {
          if (!consumer.apply(element)) {
            return;
          }
        }
      }, progressIndicator);
  }

  @Override
  public boolean isEmptyPatternSupported() {
    return true;
  }

  @Override
  public boolean isShownInSeparateTab() {
    return false;
  }
}
