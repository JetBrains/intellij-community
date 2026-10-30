// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch;

import com.intellij.lang.LangBundle;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.PerFileMappings;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

import static com.intellij.openapi.util.Conditions.not;

/**
 * Changes the language mapping of the selected files under a {@link RootType}.
 * The scratch plugin registers it for the Scratches root; the SQL dialect switcher extends it for database consoles.
 */
@ApiStatus.Internal
public abstract class ChangeLanguageAction extends DumbAwareAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    JBIterable<VirtualFile> files = JBIterable.of(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY));
    if (project == null || files.isEmpty()) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    Condition<VirtualFile> isScratch = fileFilter(project);
    if (!files.filter(not(isScratch)).isEmpty()) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    Set<String> languages = files
      .filter(isScratch)
      .map(file -> {
        // language substitution is not invoked for AbstractFileType (non-LanguageFileType)
        Language language = LanguageUtil.getFileLanguage(file) == null ? null : fileLanguage(project, file);
        if (language != null) return language.getDisplayName();
        return fileTypeManager.getFileTypeByFileName(file.getName()).getDisplayName();
      })
      .toSet();
    String langName = languages.size() == 1 ? languages.iterator().next() :
                      LangBundle.message("scratch.file.actions.0.different.languages.number", languages.size());
    e.getPresentation().setText(getChangeLanguageActionName(langName));
    e.getPresentation().setEnabledAndVisible(true);
  }

  protected @NotNull @Nls String getChangeLanguageActionName(@NotNull String languageName) {
    return LangBundle.message("scratch.file.action.change.language.action", languageName);
  }

  protected @NotNull @Nls String getChangeLanguageTitle() {
    return LangBundle.message("scratch.file.action.change.language.title");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    JBIterable<VirtualFile> files = JBIterable.of(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)).filter(fileFilter(project));
    if (files.isEmpty()) return;
    actionPerformedImpl(e, project, getChangeLanguageTitle(), files);
  }

  /** By default, every file under a visible root type; subclasses narrow it to their own root. */
  protected @NotNull Condition<VirtualFile> fileFilter(@NotNull Project project) {
    return file -> !file.isDirectory() && ScratchUtil.isScratch(file);
  }

  protected @Nullable Language fileLanguage(@NotNull Project project,
                                            @NotNull VirtualFile file) {
    Language lang = ScratchFileService.getInstance().getScratchesMapping().getMapping(file);
    return lang != null ? lang : LanguageUtil.getLanguageForPsi(project, file);
  }

  protected void actionPerformedImpl(@NotNull AnActionEvent e,
                                     @NotNull Project project,
                                     @NotNull @NlsContexts.PopupTitle String title,
                                     @NotNull JBIterable<? extends VirtualFile> files) {
    ScratchFileService fileService = ScratchFileService.getInstance();
    PerFileMappings<Language> mapping = fileService.getScratchesMapping();
    VirtualFile[] filesCopy = VfsUtilCore.toVirtualFileArray(JBIterable.from((Iterable<? extends VirtualFile>)files).toList());
    Arrays.sort(filesCopy, (o1, o2) -> StringUtil.compare(o1.getName(), o2.getName(), !o1.isCaseSensitive()));
    ScratchLanguageUtil.buildLanguagesPopup(project, title)
      .onChosen(item -> ScratchLanguageUtil.changeLanguage(project, item, filesCopy, mapping))
      .buildPopup()
      .showCenteredInCurrentWindow(project);
  }
}
