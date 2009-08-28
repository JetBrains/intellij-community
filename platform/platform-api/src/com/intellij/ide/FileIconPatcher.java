/*
 * @author max
 */
package com.intellij.ide;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface FileIconPatcher {
  ExtensionPointName<FileIconPatcher> EP_NAME = ExtensionPointName.create("com.intellij.fileIconPatcher");

  Icon patchIcon(Icon baseIcon, VirtualFile file, int flags, @Nullable Project project);
}