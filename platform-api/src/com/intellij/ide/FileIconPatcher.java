/*
 * @author max
 */
package com.intellij.ide;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;

public interface FileIconPatcher {
  Icon patchIcon(Icon baseIcon, VirtualFile file, int flags, Project project);
}