// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.GotoFileAction;
import com.intellij.ide.util.gotoByName.FilteringGotoByModel;
import com.intellij.ide.util.gotoByName.GotoFileModel;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.ui.IdeUICustomization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Konstantin Bulenkov
 */
public class FileSearchEverywhereContributor extends AbstractGotoSEContributor<FileType> {

  public FileSearchEverywhereContributor(Project project) {
    super(project);
  }

  @NotNull
  @Override
  public String getGroupName() {
    return "Files";
  }

  @Override
  public String includeNonProjectItemsText() {
    return IdeBundle.message("checkbox.include.non.project.files", IdeUICustomization.getInstance().getProjectConceptName());
  }

  @Override
  public int getSortWeight() {
    return 200;
  }

  @Override
  protected FilteringGotoByModel<FileType> createModel(Project project) {
    return new GotoFileModel(project){
      @Override
      public boolean isSlashlessMatchingEnabled() {
        return false;
      }
    };
  }

  @Override
  public boolean processSelectedItem(Object selected, int modifiers) {
    //todo maybe another elements types
    if (selected instanceof PsiElement) {
      NavigationUtil.activateFileWithPsiElement((PsiElement) selected, (modifiers & InputEvent.SHIFT_MASK) != 0);
    }

    return true;
  }

  @Override
  public Object getDataForItem(Object element, String dataId) {
    if (CommonDataKeys.PSI_FILE.is(dataId)) {
      return element;
    }

    return super.getDataForItem(element, dataId);
  }

  @Override
  protected boolean isDumbModeSupported() {
    return true;
  }

  public static class Factory implements SearchEverywhereContributorFactory<FileType> {
    @NotNull
    @Override
    public SearchEverywhereContributor<FileType> createContributor(AnActionEvent initEvent) {
      return new FileSearchEverywhereContributor(initEvent.getProject());
    }

    @Nullable
    @Override
    public SearchEverywhereContributorFilter<FileType> createFilter() {
      List<FileType> items = Stream.of(FileTypeManager.getInstance().getRegisteredFileTypes())
                                   .sorted(GotoFileAction.FileTypeComparator.INSTANCE)
                                   .collect(Collectors.toList());
      return new SearchEverywhereContributorFilterImpl<>(items, FileType::getName, FileType::getIcon);
    }
  }
}
