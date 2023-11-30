// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.FilenameFilter;
import java.util.List;

/**
 * Shows file/folder selection dialog.
 * <p>
 * Options can be customized via {@link FileChooserDescriptor}.
 */
public final class FileChooser {
  private static final Logger LOG = Logger.getInstance(FileChooser.class);

  /**
   * Normally, callback isn't invoked if a chooser was cancelled.
   * If the situation should be handled separately this interface may be used.
   */
  public interface FileChooserConsumer extends Consumer<List<VirtualFile>> {
    void cancelled();
  }

  private FileChooser() { }

  public static VirtualFile @NotNull [] chooseFiles(final @NotNull FileChooserDescriptor descriptor,
                                                    final @Nullable Project project,
                                                    final @Nullable VirtualFile toSelect) {
    return chooseFiles(descriptor, null, project, toSelect);
  }

  public static VirtualFile @NotNull [] chooseFiles(final @NotNull FileChooserDescriptor descriptor,
                                                    final @Nullable Component parent,
                                                    final @Nullable Project project,
                                                    final @Nullable VirtualFile toSelect) {
    final FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, parent);
    return chooser.choose(project, toSelect);
  }

  public static @Nullable VirtualFile chooseFile(final @NotNull FileChooserDescriptor descriptor,
                                                 final @Nullable Project project,
                                                 final @Nullable VirtualFile toSelect) {
    return chooseFile(descriptor, null, project, toSelect);
  }

  public static @Nullable VirtualFile chooseFile(final @NotNull FileChooserDescriptor descriptor,
                                                 final @Nullable Component parent,
                                                 final @Nullable Project project,
                                                 final @Nullable VirtualFile toSelect) {
    Component parentComponent = parent == null ? KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() : parent;
    LOG.assertTrue(!descriptor.isChooseMultiple());
    return ArrayUtil.getFirstElement(chooseFiles(descriptor, parentComponent, project, toSelect));
  }

  /**
   * Shows file/folder open dialog, allows user to choose files/folders and then passes result to callback in EDT.
   * On MacOS Open Dialog will be shown with slide effect if Macish UI is turned on.
   *
   * @param descriptor file chooser descriptor
   * @param project    project
   * @param toSelect   file to preselect
   * @param callback   invoked after user closes dialog, and only if there are selected files
   * @see FileChooserConsumer
   */
  public static void chooseFiles(final @NotNull FileChooserDescriptor descriptor,
                                 final @Nullable Project project,
                                 final @Nullable VirtualFile toSelect,
                                 final @NotNull Consumer<? super List<VirtualFile>> callback) {
    chooseFiles(descriptor, project, null, toSelect, callback);
  }

  /**
   * Shows file/folder open dialog, allows user to choose files/folders and then passes result to callback in EDT.
   * On MacOS Open Dialog will be shown with slide effect if Macish UI is turned on.
   *
   * @param descriptor file chooser descriptor
   * @param project    project
   * @param parent     parent component
   * @param toSelect   file to preselect
   * @param callback   invoked after user closes dialog, and only if there are selected files
   * @see FileChooserConsumer
   */
  public static void chooseFiles(final @NotNull FileChooserDescriptor descriptor,
                                 final @Nullable Project project,
                                 final @Nullable Component parent,
                                 final @Nullable VirtualFile toSelect,
                                 final @NotNull Consumer<? super List<VirtualFile>> callback) {
    Component parentComponent = parent == null ? WindowManager.getInstance().suggestParentWindow(project) : parent;
    final FileChooserFactory factory = FileChooserFactory.getInstance();
    final PathChooserDialog pathChooser = factory.createPathChooser(descriptor, project, parentComponent);
    pathChooser.choose(toSelect, callback);
  }

  /**
   * Shows file/folder open dialog, allows user to choose file/folder and then passes result to callback in EDT.
   * On MacOS Open Dialog will be shown with slide effect if Macish UI is turned on.
   *
   * @param descriptor file chooser descriptor
   * @param project    project
   * @param toSelect   file to preselect
   * @param callback   invoked after user closes dialog, and only if there is selected file
   */
  public static void chooseFile(final @NotNull FileChooserDescriptor descriptor,
                                final @Nullable Project project,
                                final @Nullable VirtualFile toSelect,
                                final @NotNull Consumer<? super VirtualFile> callback) {
    chooseFile(descriptor, project, null, toSelect, callback);
  }

  /**
   * Shows file/folder open dialog, allows user to choose file/folder and then passes result to callback in EDT.
   * On MacOS Open Dialog will be shown with slide effect if Macish UI is turned on.
   *
   * @param descriptor file chooser descriptor
   * @param project    project
   * @param parent     parent component
   * @param toSelect   file to preselect
   * @param callback   invoked after user closes dialog, and only if there is selected file
   */
  public static void chooseFile(final @NotNull FileChooserDescriptor descriptor,
                                final @Nullable Project project,
                                final @Nullable Component parent,
                                final @Nullable VirtualFile toSelect,
                                final @NotNull Consumer<? super VirtualFile> callback) {
    LOG.assertTrue(!descriptor.isChooseMultiple());
    chooseFiles(descriptor, project, parent, toSelect, files -> callback.consume(files.get(0)));
  }

  public static @NotNull FilenameFilter safeInvokeFilter(@NotNull FilenameFilter filter, boolean defaultValue) {
    return (dir, name) -> {
      try {
        return filter.accept(dir, name);
      }
      catch (Throwable e) {
        LOG.error(e);
        return defaultValue;
      }
    };
  }
}
