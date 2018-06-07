// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.GotoFileAction;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.FilteringGotoByModel;
import com.intellij.ide.util.gotoByName.GotoFileModel;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.ui.IdeUICustomization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.util.List;
import java.util.regex.Matcher;
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
  public boolean processSelectedItem(Object selected, int modifiers, String searchText) {
    if (selected instanceof PsiFile) {
      VirtualFile file = ((PsiFile)selected).getVirtualFile();
      if (file != null) {
        Pair<Integer, Integer> pos = getLineAndColumn(searchText);
        OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, file, pos.first, pos.second);
        descriptor.setUseCurrentWindow((modifiers & InputEvent.SHIFT_MASK) == 0);
        if (descriptor.canNavigate()) {
          descriptor.navigate(true);
          return true;
        }
      }
    }

    return super.processSelectedItem(selected, modifiers, searchText);
  }

  private static Pair<Integer, Integer> getLineAndColumn(String text) {
    int line = getLineAndColumnRegexpGroup(text, 2);
    int column = getLineAndColumnRegexpGroup(text, 3);

    if (line != -1) {
      column = 0;
    }

    return new Pair<>(line, column);
  }

  private static int getLineAndColumnRegexpGroup(String text, int groupNumber) {
    final Matcher matcher = ChooseByNamePopup.patternToDetectLinesAndColumns.matcher(text);
    if (matcher.matches()) {
      try {
        if (groupNumber <= matcher.groupCount()) {
          final String group = matcher.group(groupNumber);
          if (group != null) return Integer.parseInt(group) - 1;
        }
      }
      catch (NumberFormatException ignored) {
      }
    }

    return -1;
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
