package com.intellij.psi.impl.include;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.FileContent;
import com.intellij.psi.PsiFileSystemItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class FileIncludeProvider {

  public static final ExtensionPointName<FileIncludeProvider> EP_NAME = ExtensionPointName.create("com.intellij.include.provider");

  @NotNull
  public abstract String getId();

  public abstract boolean acceptFile(VirtualFile file);
  
  @NotNull
  public abstract FileIncludeInfo[] getIncludeInfos(FileContent content);

  @Nullable
  public VirtualFile resolveInclude(FileIncludeInfo include, VirtualFile context, Project project) {
    PsiFileSystemItem fileSystemItem = FileIncludeManager.getManager(project).resolveFileReference(include.path, context);
    return fileSystemItem == null ? null : fileSystemItem.getVirtualFile();
  }
}
