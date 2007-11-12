/*
 * @author max
 */
package com.intellij.ide;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface FileIconPatcher {
  @NotNull
  Icon patchIcon(Icon baseIcon, VirtualFile file, int flags, Project project);
}