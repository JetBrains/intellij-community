/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class FileTypeManager implements SettingsSavingComponent {
  public static FileTypeManager getInstance() {
    return ApplicationManager.getApplication().getComponent(FileTypeManager.class);
  }

  public abstract void registerFileType(FileType type, String[] defaultAssociatedExtensions);

  public abstract FileType getFileTypeByFileName(String fileName);

  public abstract FileType getFileTypeByFile(VirtualFile file);

  public abstract FileType getFileTypeByExtension(String extension);

  public abstract FileType[] getRegisteredFileTypes();

  public abstract boolean isFileIgnored(String name);

  public abstract String[] getAssociatedExtensions(FileType type);

  public abstract void addFileTypeListener(FileTypeListener listener);

  public abstract void removeFileTypeListener(FileTypeListener listener);

  public abstract void dispatchPendingEvents(FileTypeListener listener);

  public abstract FileType getKnownFileTypeOrAssociate(VirtualFile file);
}
