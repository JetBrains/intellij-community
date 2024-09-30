// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Allows customizing {@link FileChooser} dialog options.
 * <p>
 * Please consider using common variants provided by {@link FileChooserDescriptorFactory}.
 */
public class FileChooserDescriptor implements Cloneable {
  @ApiStatus.Internal
  public static final DataKey<String> FILTER_TYPE = DataKey.create("file.chooser.filter.kind");

  private final boolean myChooseFiles;
  private final boolean myChooseFolders;
  private final boolean myChooseJars;
  private final boolean myChooseJarsAsFiles;
  private final boolean myChooseJarContents;
  private final boolean myChooseMultiple;

  private @NlsContexts.DialogTitle String myTitle = IdeCoreBundle.message("file.chooser.default.title");
  private @NlsContexts.Label String myDescription;
  private boolean myHideIgnored = true;
  private final List<VirtualFile> myRoots = new ArrayList<>();
  private boolean myShowFileSystemRoots = true;
  private boolean myTreeRootVisible = false;
  private boolean myShowHiddenFiles = false;
  private @Nullable Pair<@Nls String, List<String>> myExtensionFilter = null;
  private FileType @Nullable [] myFileTypeFilter = null;
  private @Nullable Predicate<? super VirtualFile> myFileFilter = null;
  private boolean myForcedToUseIdeaFileChooser = false;

  private final Map<String, Object> myUserData = new HashMap<>();

  /**
   * Use {@link FileChooserDescriptorFactory} for most used descriptors.
   *
   * @param chooseFiles       controls whether files can be chosen
   * @param chooseFolders     controls whether folders can be chosen
   * @param chooseJars        controls whether .jar files can be chosen
   * @param chooseJarsAsFiles controls whether .jar files will be returned as files or as folders
   * @param chooseJarContents controls whether .jar file contents can be chosen
   * @param chooseMultiple    controls whether multiple files can be chosen
   */
  public FileChooserDescriptor(
    boolean chooseFiles,
    boolean chooseFolders,
    boolean chooseJars,
    boolean chooseJarsAsFiles,
    boolean chooseJarContents,
    boolean chooseMultiple
  ) {
    myChooseFiles = chooseFiles;
    myChooseFolders = chooseFolders;
    myChooseJars = chooseJars;
    myChooseJarsAsFiles = chooseJarsAsFiles;
    myChooseJarContents = chooseJarContents;
    myChooseMultiple = chooseMultiple;
  }

  public FileChooserDescriptor(@NotNull FileChooserDescriptor d) {
    this(d.isChooseFiles(), d.isChooseFolders(), d.isChooseJars(), d.isChooseJarsAsFiles(), d.isChooseJarContents(), d.isChooseMultiple());
    myTitle = d.getTitle();
    myDescription = d.getDescription();
    myHideIgnored = d.isHideIgnored();
    myRoots.addAll(d.getRoots());
    myShowFileSystemRoots = d.isShowFileSystemRoots();
    myTreeRootVisible = d.isTreeRootVisible();
    myShowHiddenFiles = d.isShowHiddenFiles();
    myExtensionFilter = d.myExtensionFilter;
    myFileTypeFilter = d.myFileTypeFilter;
    myFileFilter = d.myFileFilter;
    myForcedToUseIdeaFileChooser = false;
    myUserData.putAll(d.myUserData);
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

  public @NlsContexts.DialogTitle String getTitle() {
    return myTitle;
  }

  public void setTitle(@NlsContexts.DialogTitle String title) {
    withTitle(title);
  }

  public FileChooserDescriptor withTitle(@NlsContexts.DialogTitle String title) {
    myTitle = title;
    return this;
  }

  public @NlsContexts.Label String getDescription() {
    return myDescription;
  }

  public void setDescription(@NlsContexts.Label String description) {
    withDescription(description);
  }

  public FileChooserDescriptor withDescription(@NlsContexts.Label String description) {
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

  public void setRoots(VirtualFile @NotNull ... roots) {
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
   * <p/>
   * In native choosers, has no effect on visibility (use {@link #withExtensionFilter} for that), only on a final eligibility check.
   * So for simple file type- or extension-based filtering it is better to use {@link #withExtensionFilter}.
   */
  public FileChooserDescriptor withFileFilter(@Nullable Condition<? super VirtualFile> filter) {
    myFileFilter = filter;
    return this;
  }

  /**
   * @see #withExtensionFilter(String, String...)
   * @see FileChooserDescriptorFactory#createSingleFileDescriptor(FileType)
   * @see FileChooserDescriptorFactory#createSingleFileOrFolderDescriptor(FileType)
   */
  public FileChooserDescriptor withExtensionFilter(@NotNull FileType type) {
    return withExtensionFilter(IdeCoreBundle.message("file.chooser.files.label", type.getName()), type);
  }

  /**
   * @see #withExtensionFilter(String, String...)
   */
  public FileChooserDescriptor withExtensionFilter(@NlsContexts.Label @NotNull String label, @NotNull FileType @NotNull ... types) {
    if (types.length == 0) throw new IllegalArgumentException("The list must not be empty");
    var extensions = Stream.of(types)
      .flatMap(type -> FileTypeManager.getInstance().getAssociations(type).stream())
      .map(matcher -> matcher instanceof ExtensionFileNameMatcher em ? em.getExtension() : null)
      .filter(Objects::nonNull)
      .toArray(String[]::new);
    myFileTypeFilter = types;
    return withExtensionFilter(label, extensions);
  }

  /**
   * @see #withExtensionFilter(String, String...)
   * @see FileChooserDescriptorFactory#createSingleFileDescriptor(String)
   */
  public FileChooserDescriptor withExtensionFilter(@NotNull String extension) {
    return withExtensionFilter(IdeCoreBundle.message("file.chooser.files.label", extension.toUpperCase(Locale.ROOT)), extension);
  }

  /**
   * Adds a simple filter based on file extensions that is compatible with native (OS) file chooser dialogs.
   * The behavior is platform-dependent (some dialogs make non-matching files non-selectable, others hide them completely).
   * The {@code label} parameter is used in a combobox to switch between showing only matching or all files
   * in dialogs supporting this feature.
   */
  public FileChooserDescriptor withExtensionFilter(@NlsContexts.Label @NotNull String label, @NotNull String @NotNull ... extensions) {
    if (extensions.length == 0) throw new IllegalArgumentException("The list must not be empty");
    if (ContainerUtil.find(extensions, String::isBlank) != null) throw new IllegalArgumentException("The list must not contain empty strings");
    myExtensionFilter = new Pair<>(label, List.of(extensions));
    return this;
  }

  public FileChooserDescriptor withoutExtensionFilter() {
    myExtensionFilter = null;
    myFileTypeFilter = null;
    return this;
  }

  /**
   * Defines whether a file is visible in the tree.
   *
   * @deprecated ignored by native file choosers; use {@link #withFileFilter} and {@link #withExtensionFilter} instead.
   */
  @Deprecated
  @ApiStatus.NonExtendable
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
      if (myExtensionFilter != null && !matchesExtFilter(file, myExtensionFilter.second)) {
        return false;
      }
      if (myFileTypeFilter != null && !matchesTypeFilter(file, myFileTypeFilter)) {
        return false;
      }
      if (myFileFilter != null && !myFileFilter.test(file)) {
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
  @Contract("null -> false")
  public boolean isFileSelectable(@Nullable VirtualFile file) {
    if (file == null) return false;

    if (file.is(VFileProperty.SYMLINK) && file.getCanonicalPath() == null) {
      return false;
    }
    if (file.isDirectory() && myChooseFolders) {
      return true;
    }
    if (!file.isDirectory()) {
      if (myExtensionFilter != null) {
        return matchesExtFilter(file, myExtensionFilter.second);
      }
      if (myFileTypeFilter != null) {
        return matchesTypeFilter(file, myFileTypeFilter);
      }
      if (myFileFilter != null) {
        return myFileFilter.test(file);
      }
    }
    return acceptAsJarFile(file) || acceptAsGeneralFile(file);
  }

  private static boolean matchesExtFilter(VirtualFile file, List<String> extensions) {
    return ContainerUtil.exists(extensions, ext -> Strings.endsWithIgnoreCase(file.getName(), '.' + ext));
  }

  private static boolean matchesTypeFilter(VirtualFile file, FileType[] types) {
    return ContainerUtil.exists(types, type -> FileTypeRegistry.getInstance().isFileOfType(file, type));
  }

  /**
   * Called upon <em>OK</em> action before closing dialog.
   * Override to customize validation of user input.
   *
   * @param files selected files to be checked
   * @throws Exception if selected files cannot be accepted, the exception message will be shown in the UI.
   */
  public void validateSelectedFiles(@NotNull VirtualFile @NotNull [] files) throws Exception { }

  public Icon getIcon(final VirtualFile file) {
    return dressIcon(file, IconUtil.getIcon(file, Iconable.ICON_FLAG_READ_STATUS, null));
  }

  protected static Icon dressIcon(final VirtualFile file, final Icon baseIcon) {
    return file.isValid() && file.is(VFileProperty.SYMLINK) ? LayeredIcon.layeredIcon(new Icon[]{baseIcon, PlatformIcons.SYMLINK_ICON}) : baseIcon;
  }

  public String getName(final VirtualFile file) {
    return file.getPresentableName();
  }

  public @NlsSafe @Nullable String getComment(final VirtualFile file) {
    return null;
  }

  private boolean acceptAsGeneralFile(VirtualFile file) {
    if (FileElement.isArchive(file)) return false; // should be handled by `acceptAsJarFile`
    return !file.isDirectory() && myChooseFiles;
  }

  public boolean isForcedToUseIdeaFileChooser() {
    return myForcedToUseIdeaFileChooser;
  }

  public void setForcedToUseIdeaFileChooser(boolean forcedToUseIdeaFileChooser) {
    myForcedToUseIdeaFileChooser = forcedToUseIdeaFileChooser;
  }

  @ApiStatus.Internal
  public final @Nullable VirtualFile getFileToSelect(@NotNull VirtualFile file) {
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

  private boolean acceptAsJarFile(VirtualFile file) {
    return myChooseJars && FileElement.isArchive(file);
  }

  @ApiStatus.Internal
  public @Nullable Pair<@Nls String, List<String>> getExtensionFilter() {
    return myExtensionFilter;
  }

  /** @deprecated use the copy constructor ({@link #FileChooserDescriptor(FileChooserDescriptor)}) instead */
  @Deprecated(forRemoval = true)
  @Override
  public final Object clone() {
    try {
      return super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  public @Nullable Object getUserData(@NotNull String dataId) {
    return myUserData.get(dataId);
  }

  public @Nullable <T> T getUserData(@NotNull DataKey<T> key) {
    if (key == FILTER_TYPE) {
      var result = "";
      if (myExtensionFilter != null) result += 'e';
      if (myFileFilter != null) result += 'f';
      @SuppressWarnings("unchecked") T t = (T)result;
      return t;
    }
    else {
      @SuppressWarnings("unchecked") T t = (T)myUserData.get(key.getName());
      return t;
    }
  }

  public <T> void putUserData(@NotNull DataKey<T> key, @Nullable T data) {
    myUserData.put(key.getName(), data);
  }

  @Override
  public String toString() {
    return "FileChooserDescriptor [" + myTitle + "]";
  }
}
