// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.UIBundle;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import gnu.trove.THashMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @see FileChooserDescriptorFactory
 */
public class FileChooserDescriptor implements Cloneable {
  private final boolean myChooseFiles;
  private final boolean myChooseFolders;
  private final boolean myChooseJars;
  private final boolean myChooseJarsAsFiles;
  private final boolean myChooseJarContents;
  private final boolean myChooseMultiple;

  private String myTitle = UIBundle.message("file.chooser.default.title");
  private String myDescription;

  private boolean myHideIgnored = true;
  private final List<VirtualFile> myRoots = new ArrayList<>();
  private boolean myShowFileSystemRoots = true;
  private boolean myTreeRootVisible = false;
  private boolean myShowHiddenFiles = false;
  private Condition<? super VirtualFile> myFileFilter = null;
  private boolean myForcedToUseIdeaFileChooser = false;

  private final Map<String, Object> myUserData = new THashMap<>();

  /**
   * Creates new instance. Use methods from {@link FileChooserDescriptorFactory} for most used descriptors.
   *
   * @param chooseFiles       controls whether files can be chosen
   * @param chooseFolders     controls whether folders can be chosen
   * @param chooseJars        controls whether .jar files can be chosen
   * @param chooseJarsAsFiles controls whether .jar files will be returned as files or as folders
   * @param chooseJarContents controls whether .jar file contents can be chosen
   * @param chooseMultiple    controls how many files can be chosen
   */
  public FileChooserDescriptor(boolean chooseFiles,
                               boolean chooseFolders,
                               boolean chooseJars,
                               boolean chooseJarsAsFiles,
                               boolean chooseJarContents,
                               boolean chooseMultiple) {
    myChooseFiles = chooseFiles;
    myChooseFolders = chooseFolders;
    myChooseJars = chooseJars;
    myChooseJarsAsFiles = chooseJarsAsFiles;
    myChooseJarContents = chooseJarContents;
    myChooseMultiple = chooseMultiple;
  }

  public FileChooserDescriptor(@NotNull FileChooserDescriptor d) {
    this(d.isChooseFiles(), d.isChooseFolders(), d.isChooseJars(), d.isChooseJarsAsFiles(), d.isChooseJarContents(), d.isChooseMultiple());
    withTitle(d.getTitle());
    withDescription(d.getDescription());
    withHideIgnored(d.isHideIgnored());
    withRoots(d.getRoots());
    withShowFileSystemRoots(d.isShowFileSystemRoots());
    withTreeRootVisible(d.isTreeRootVisible());
    withShowHiddenFiles(d.isShowHiddenFiles());
  }

  public boolean isChooseFiles() {
    return myChooseFiles;
  }

  public boolean isChooseFolders() {
    return myChooseFolders;
  }

  public boolean isChooseJars() {
    return myChooseJars;
  }

  public boolean isChooseJarsAsFiles() {
    return myChooseJarsAsFiles;
  }

  public boolean isChooseJarContents() {
    return myChooseJarContents;
  }

  public boolean isChooseMultiple() {
    return myChooseMultiple;
  }

  public String getTitle() {
    return myTitle;
  }

  public void setTitle(@Nls(capitalization = Nls.Capitalization.Title) String title) {
    withTitle(title);
  }

  public FileChooserDescriptor withTitle(@Nls(capitalization = Nls.Capitalization.Title) String title) {
    myTitle = title;
    return this;
  }

  public String getDescription() {
    return myDescription;
  }

  public void setDescription(@Nls(capitalization = Nls.Capitalization.Sentence) String description) {
    withDescription(description);
  }

  public FileChooserDescriptor withDescription(@Nls(capitalization = Nls.Capitalization.Sentence) String description) {
    myDescription = description;
    return this;
  }

  public boolean isHideIgnored() {
    return myHideIgnored;
  }

  public void setHideIgnored(boolean hideIgnored) {
    withHideIgnored(hideIgnored);
  }

  public FileChooserDescriptor withHideIgnored(boolean hideIgnored) {
    myHideIgnored = hideIgnored;
    return this;
  }

  public List<VirtualFile> getRoots() {
    return Collections.unmodifiableList(myRoots);
  }

  public void setRoots(@NotNull VirtualFile... roots) {
    withRoots(roots);
  }

  public void setRoots(@NotNull List<? extends VirtualFile> roots) {
    withRoots(roots);
  }

  public FileChooserDescriptor withRoots(final VirtualFile... roots) {
    return withRoots(Arrays.asList(roots));
  }

  public FileChooserDescriptor withRoots(@NotNull List<? extends VirtualFile> roots) {
    if (roots.contains(null)) throw new IllegalArgumentException("'null' in roots: " + roots);
    myRoots.clear();
    myRoots.addAll(roots);
    return this;
  }

  public boolean isShowFileSystemRoots() {
    return myShowFileSystemRoots;
  }

  public void setShowFileSystemRoots(boolean showFileSystemRoots) {
    withShowFileSystemRoots(showFileSystemRoots);
  }

  public FileChooserDescriptor withShowFileSystemRoots(boolean showFileSystemRoots) {
    myShowFileSystemRoots = showFileSystemRoots;
    return this;
  }

  public boolean isTreeRootVisible() {
    return myTreeRootVisible;
  }

  public FileChooserDescriptor withTreeRootVisible(boolean isTreeRootVisible) {
    myTreeRootVisible = isTreeRootVisible;
    return this;
  }

  public boolean isShowHiddenFiles() {
    return myShowHiddenFiles;
  }

  public FileChooserDescriptor withShowHiddenFiles(boolean showHiddenFiles) {
    myShowHiddenFiles = showHiddenFiles;
    return this;
  }

  /**
   * Sets simple boolean condition for use in {@link #isFileVisible(VirtualFile, boolean)} and {@link #isFileSelectable(VirtualFile)}.
   */
  public FileChooserDescriptor withFileFilter(@Nullable Condition<? super VirtualFile> filter) {
    myFileFilter = filter;
    return this;
  }

  /**
   * Defines whether a file is visible in the tree.
   */
  public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
    if (file.is(VFileProperty.SYMLINK) && file.getCanonicalPath() == null) {
      return false;
    }

    if (!file.isDirectory()) {
      if (FileElement.isArchive(file)) {
        if (!myChooseJars && !myChooseJarContents) {
          return false;
        }
      }
      else if (!myChooseFiles) {
        return false;
      }
      if (myFileFilter != null && !myFileFilter.value(file)) {
        return false;
      }
    }

    if (isHideIgnored() && FileTypeManager.getInstance().isFileIgnored(file)) {
      return false;
    }

    if (!showHiddenFiles && FileElement.isFileHidden(file)) {
      return false;
    }

    return true;
  }

  /**
   * Defines whether a file can be chosen.
   */
  public boolean isFileSelectable(VirtualFile file) {
    if (file == null) return false;

    if (file.is(VFileProperty.SYMLINK) && file.getCanonicalPath() == null) {
      return false;
    }
    if (file.isDirectory() && myChooseFolders) {
      return true;
    }

    if (myFileFilter != null && !file.isDirectory()) {
      return myFileFilter.value(file);
    }

    return acceptAsJarFile(file) || acceptAsGeneralFile(file);
  }

  public Icon getIcon(final VirtualFile file) {
    return dressIcon(file, IconUtil.getIcon(file, Iconable.ICON_FLAG_READ_STATUS, null));
  }

  protected static Icon dressIcon(final VirtualFile file, final Icon baseIcon) {
    return file.isValid() && file.is(VFileProperty.SYMLINK) ? new LayeredIcon(baseIcon, PlatformIcons.SYMLINK_ICON) : baseIcon;
  }

  public String getName(final VirtualFile file) {
    return file.getPath();
  }

  @Nullable
  public String getComment(final VirtualFile file) {
    return null;
  }

  /**
   * the method is called upon pressing Ok in the FileChooserDialog
   * Override the method in order to customize validation of user input
   * @param files - selected files to be checked
   * @throws Exception if the the files cannot be accepted
   */
  public void validateSelectedFiles(@NotNull VirtualFile[] files) throws Exception {
  }

  public boolean isForcedToUseIdeaFileChooser() {
    return myForcedToUseIdeaFileChooser;
  }

  public void setForcedToUseIdeaFileChooser(boolean forcedToUseIdeaFileChooser) {
    myForcedToUseIdeaFileChooser = forcedToUseIdeaFileChooser;
  }

  private boolean acceptAsGeneralFile(VirtualFile file) {
    if (FileElement.isArchive(file)) return false; // should be handle by acceptsAsJarFile
    return !file.isDirectory() && myChooseFiles;
  }

  private boolean acceptAsJarFile(VirtualFile file) {
    return myChooseJars && FileElement.isArchive(file);
  }

  @Nullable
  public final VirtualFile getFileToSelect(VirtualFile file) {
    if (file.isDirectory() && (myChooseFolders || isFileSelectable(file))) {
      return file;
    }
    boolean isJar = FileTypeRegistry.getInstance().isFileOfType(file, ArchiveFileType.INSTANCE);
    if (!isJar) {
      return acceptAsGeneralFile(file) ? file : null;
    }
    if (myChooseJarsAsFiles) {
      return file;
    }
    if (!acceptAsJarFile(file)) {
      return null;
    }
    String path = file.getPath();
    return JarFileSystem.getInstance().findFileByPath(path + JarFileSystem.JAR_SEPARATOR);
  }

  @Override
  public final Object clone() {
    try {
      return super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  @Nullable
  public Object getUserData(@NotNull String dataId) {
    return myUserData.get(dataId);
  }

  @Nullable
  public <T> T getUserData(@NotNull DataKey<T> key) {
    @SuppressWarnings({"unchecked"}) final T t = (T)myUserData.get(key.getName());
    return t;
  }

  public <T> void putUserData(@NotNull DataKey<T> key, @Nullable T data) {
    myUserData.put(key.getName(), data);
  }

  @Override
  public String toString() {
    return "FileChooserDescriptor [" + myTitle + "]";
  }
}
