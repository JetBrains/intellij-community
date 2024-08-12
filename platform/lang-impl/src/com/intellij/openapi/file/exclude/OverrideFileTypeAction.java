// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.file.exclude;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

final class OverrideFileTypeAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    VirtualFile[] files = getContextFiles(e, file -> OverrideFileTypeManager.getInstance().getFileValue(file) == null);
    boolean enabled = files.length != 0;
    Presentation presentation = e.getPresentation();
    presentation.setDescription(enabled
                                ? ActionsBundle.message("action.OverrideFileTypeAction.verbose.description", files[0].getName(), files.length - 1)
                                : ActionsBundle.message("action.OverrideFileTypeAction.description"));
    presentation.setEnabledAndVisible(enabled);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VirtualFile[] files = getContextFiles(e, file->OverrideFileTypeManager.getInstance().getFileValue(file) == null);
    if (files.length == 0) return;
    DefaultActionGroup group = new DefaultActionGroup();
    // although well-behaved types have unique names, file types coming from plugins can be wild
    Map<String, List<String>> duplicates = Arrays.stream(FileTypeManager.getInstance().getRegisteredFileTypes())
      .map(t -> t.getDisplayName())
      .collect(Collectors.groupingBy(Function.identity()));

    for (FileType type : ContainerUtil.sorted(Arrays.asList(FileTypeManager.getInstance().getRegisteredFileTypes()),
                                              (f1,f2)->f1.getDisplayName().compareToIgnoreCase(f2.getDisplayName()))) {
      if (!OverrideFileTypeManager.isOverridable(type)) continue;
      boolean hasDuplicate = duplicates.get(type.getDisplayName()).size() > 1;
      String dupHint = null;
      if (hasDuplicate) {
        PluginDescriptor descriptor = ((FileTypeManagerImpl)FileTypeManager.getInstance()).findPluginDescriptor(type);
        dupHint = descriptor == null ? null :
                  " (" + (descriptor.isBundled() ? ActionsBundle.message("group.OverrideFileTypeAction.bundledPlugin") :
                          ActionsBundle.message("group.OverrideFileTypeAction.fromNamedPlugin", descriptor.getName()))
                  + ")";
      }
      @NlsActions.ActionText
      String displayText = type.getDisplayName() + StringUtil.notNullize(dupHint);
      group.add(new ChangeToThisFileTypeAction(displayText, files, type));
    }
    JBPopupFactory.getInstance()
      .createActionGroupPopup(ActionsBundle.message("group.OverrideFileTypeAction.title"),
                              group, e.getDataContext(), false, null, -1)
      .showInBestPositionFor(e.getDataContext());
  }

  static @NotNull VirtualFile @NotNull [] getContextFiles(@NotNull AnActionEvent e, @NotNull Predicate<? super VirtualFile> additionalPredicate) {
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (files == null) return VirtualFile.EMPTY_ARRAY;
    return Arrays.stream(files)
      .filter(file -> file != null && ChangeToThisFileTypeAction.isOverridableFile(file))
      .filter(additionalPredicate)
      .toArray(count -> VirtualFile.ARRAY_FACTORY.create(count));
  }

  private static final class ChangeToThisFileTypeAction extends DumbAwareAction {
    private final @NotNull VirtualFile @NotNull [] myFiles;
    private final FileType myType;

    ChangeToThisFileTypeAction(@NotNull @NlsActions.ActionText String displayText,
                               @NotNull VirtualFile @NotNull [] files,
                               @NotNull FileType type) {
      super(displayText, ActionsBundle.message("action.ChangeToThisFileTypeAction.description", type.getDescription()), type.getIcon());
      myFiles = files;
      myType = type;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      for (VirtualFile file : myFiles) {
        if (isOverridableFile(file)) {
          OverrideFileTypeManager.getInstance().addFile(file, myType);
        }
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      boolean enabled = ContainerUtil.exists(myFiles, file -> isOverridableFile(file));
      e.getPresentation().setEnabled(enabled);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    private static boolean isOverridableFile(@NotNull VirtualFile file) {
      return file.isValid()
             && !file.isDirectory()
             && (file instanceof VirtualFileWithId)
             && OverrideFileTypeManager.isOverridable(file.getFileType());
    }
  }
}
