package com.intellij.openapi.roots.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Gregory.Shrago
 */
public interface FilePropertyPusher<T> {
  ExtensionPointName<FilePropertyPusher> EP_NAME = ExtensionPointName.create("com.intellij.filePropertyPusher");

  void initExtra(Project project, MessageBus bus, Engine languageLevelUpdater);
  @NotNull
  Key<T> getFileDataKey();
  boolean pushDirectoriesOnly();

  T getDefaultValue();

  @Nullable
  T getImmediateValue(Project project, VirtualFile file);

  @Nullable
  T getImmediateValue(Module module);

  boolean acceptsFile(VirtualFile file);

  void persistAttribute(VirtualFile fileOrDir, T value) throws IOException;

  public interface Engine {
    void pushAll();
    void pushRecursively(final VirtualFile vile, final Project project);
  }
}
