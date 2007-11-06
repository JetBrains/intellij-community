/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 06.11.2007
 * Time: 19:49:57
 */
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public interface SyntaxHighlighterProvider {
  @Nullable
  SyntaxHighlighter create(FileType fileType, @Nullable Project project, @Nullable VirtualFile file);
}