/*
 * @author max
 */
package com.intellij.ide;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface FileIconProvider {
  @Nullable
  Icon getIcon(VirtualFile file, int flags, Project project);
}