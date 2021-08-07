// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.file.exclude;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

class OverrideFileTypeAction extends AnAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    e.getPresentation().setEnabled(file != null && !file.isDirectory());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (file == null) return;
    DefaultActionGroup group = new DefaultActionGroup();
    // although well-behaved types have unique names, file types coming from plugins can be wild
    Map<String, List<String>> duplicates = Arrays.stream(FileTypeManager.getInstance().getRegisteredFileTypes())
      .map(t -> t.getDisplayName())
      .collect(Collectors.groupingBy(Function.identity()));

    for (FileType type : ContainerUtil.sorted(Arrays.asList(FileTypeManager.getInstance().getRegisteredFileTypes()),
                                              (f1,f2)->f1.getDisplayName().compareToIgnoreCase(f2.getDisplayName()))) {
      if (type instanceof InternalFileType) continue;
      if (type instanceof DirectoryFileType) continue;
      if (type instanceof UnknownFileType) continue;
      if (type instanceof FakeFileType) continue;
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
      group.add(new ChangeToThisFileTypeAction(displayText, file, type));
    }
    JBPopupFactory.getInstance()
      .createActionGroupPopup(ActionsBundle.message("group.OverrideFileTypeAction.title"),
                              group, e.getDataContext(), false, null, -1)
      .showInBestPositionFor(e.getDataContext());
  }

  private static class ChangeToThisFileTypeAction extends AnAction {
    private final VirtualFile myFile;
    private final FileType myType;

    ChangeToThisFileTypeAction(@NotNull @NlsActions.ActionText String displayText,
                               @NotNull VirtualFile file,
                               @NotNull FileType type) {
      super(displayText,
            ActionsBundle.message("action.ChangeToThisFileTypeAction.description", file.getName(), type.getDescription()), type.getIcon());
      myFile = file;
      myType = type;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      OverrideFileTypeManager.getInstance().addFile(myFile, myType);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      boolean enabled = myFile.isValid() && !myFile.isDirectory();
      e.getPresentation().setEnabled(enabled);
    }
  }
}
