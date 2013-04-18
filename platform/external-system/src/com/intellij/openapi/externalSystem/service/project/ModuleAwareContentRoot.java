package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ExcludeFolder;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 2/21/12 11:15 AM
 */
public class ModuleAwareContentRoot implements ContentEntry {

  @NotNull private final Module       myModule;
  @NotNull private final ContentEntry myDelegate;
  @NotNull private final VirtualFile  myFile;

  public ModuleAwareContentRoot(@NotNull Module module, @NotNull ContentEntry delegate) throws IllegalArgumentException {
    myDelegate = delegate;
    myModule = module;
    final VirtualFile file = delegate.getFile();
    if (file == null) {
      throw new IllegalArgumentException(String.format(
        "Detected attempt to create ModuleAwareContentRoot object for content root that points to the un-existing file - %s, module: %s",
        delegate, module
      ));
    }
    myFile = file;
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
    return myFile;
  }

  @Override
  @NotNull
  public String getUrl() {
    return myDelegate.getUrl();
  }

  @Override
  public SourceFolder[] getSourceFolders() {
    return myDelegate.getSourceFolders();
  }

  @Override
  public VirtualFile[] getSourceFolderFiles() {
    return myDelegate.getSourceFolderFiles();
  }

  @Override
  public ExcludeFolder[] getExcludeFolders() {
    return myDelegate.getExcludeFolders();
  }

  @Override
  public VirtualFile[] getExcludeFolderFiles() {
    return myDelegate.getExcludeFolderFiles();
  }

  @Override
  public SourceFolder addSourceFolder(@NotNull VirtualFile file, boolean isTestSource) {
    return myDelegate.addSourceFolder(file, isTestSource);
  }

  @Override
  public SourceFolder addSourceFolder(@NotNull VirtualFile file, boolean isTestSource, @NotNull String packagePrefix) {
    return myDelegate.addSourceFolder(file, isTestSource, packagePrefix);
  }

  @Override
  public SourceFolder addSourceFolder(@NotNull String url, boolean isTestSource) {
    return myDelegate.addSourceFolder(url, isTestSource);
  }

  @Override
  public void removeSourceFolder(@NotNull SourceFolder sourceFolder) {
    myDelegate.removeSourceFolder(sourceFolder);
  }

  @Override
  public void clearSourceFolders() {
    myDelegate.clearSourceFolders();
  }

  @Override
  public ExcludeFolder addExcludeFolder(@NotNull VirtualFile file) {
    return myDelegate.addExcludeFolder(file);
  }

  @Override
  public ExcludeFolder addExcludeFolder(@NotNull String url) {
    return myDelegate.addExcludeFolder(url);
  }

  @Override
  public void removeExcludeFolder(@NotNull ExcludeFolder excludeFolder) {
    myDelegate.removeExcludeFolder(excludeFolder);
  }

  @Override
  public void clearExcludeFolders() {
    myDelegate.clearExcludeFolders();
  }

  @Override
  public boolean isSynthetic() {
    return myDelegate.isSynthetic();
  }
}
