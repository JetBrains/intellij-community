package com.intellij.openapi.fileTypes;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author ilyas
 */
public interface ContentBasedSyntaxHighlighterProvider {

  ExtensionPointName<ContentBasedSyntaxHighlighterProvider> EP_NAME = ExtensionPointName.create("com.intellij.contentBasedSyntaxHighlighterProvider");

  boolean isApplicable(FileType fileType, Project project, VirtualFile vFile);

  @NotNull
  SyntaxHighlighter createHighlighter(FileType fileType, Project project, VirtualFile vFile);
}
