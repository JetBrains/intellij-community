// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import java.util.List;

/**
 * Shows file/folder selection dialog.
 * <p>
 * Options can be customized via {@link FileChooserDescriptor}.
 */
public final class FileChooser {
  private static final Logger LOG = Logger.getInstance(FileChooser.class);

  /**
   * Normally, callback isn't invoked if a chooser was canceled.
   * If the situation should be handled separately, this interface may be used.
   */
  public interface FileChooserConsumer extends Consumer<List<VirtualFile>> {
    void cancelled();
  }

  private FileChooser() { }

  public static VirtualFile @NotNull [] chooseFiles(@NotNull FileChooserDescriptor descriptor, @Nullable Project project, @Nullable VirtualFile toSelect) {
    return chooseFiles(descriptor, null, project, toSelect);
  }

  public static VirtualFile @NotNull [] chooseFiles(
    @NotNull FileChooserDescriptor descriptor,
    @Nullable Component parent,
    @Nullable Project project,
    @Nullable VirtualFile toSelect
  ) {
    var chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, parent);
    return chooser.choose(project, toSelect);
  }

  public static @Nullable VirtualFile chooseFile(@NotNull FileChooserDescriptor descriptor, @Nullable Project project, @Nullable VirtualFile toSelect) {
    return chooseFile(descriptor, null, project, toSelect);
  }

  public static @Nullable VirtualFile chooseFile(
    @NotNull FileChooserDescriptor descriptor,
    @Nullable Component parent,
    @Nullable Project project,
    @Nullable VirtualFile toSelect
  ) {
    var parentComponent = parent == null ? KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() : parent;
    LOG.assertTrue(!descriptor.isChooseMultiple());
    return ArrayUtil.getFirstElement(chooseFiles(descriptor, parentComponent, project, toSelect));
  }

  /**
   * Shows file/folder open dialog, allows user to choose files/folders and then passes the result to callback on EDT.
   *
   * @param descriptor file chooser descriptor
   * @param project    project
   * @param toSelect   file to preselect
   * @param callback   invoked after user closes dialog, and only if there are selected files
   * @see FileChooserConsumer
   */
  public static void chooseFiles(
    @NotNull FileChooserDescriptor descriptor,
    @Nullable Project project,
    @Nullable VirtualFile toSelect,
    @NotNull Consumer<? super List<VirtualFile>> callback
  ) {
    chooseFiles(descriptor, project, null, toSelect, callback);
  }

  /**
   * Shows file/folder open dialog, allows user to choose files/folders and then passes the result to callback on EDT.
   *
   * @param descriptor file chooser descriptor
   * @param project    project
   * @param parent     parent component
   * @param toSelect   file to preselect
   * @param callback   invoked after user closes dialog, and only if there are selected files
   * @see FileChooserConsumer
   */
  public static void chooseFiles(
    @NotNull FileChooserDescriptor descriptor,
    @Nullable Project project,
    @Nullable Component parent,
    @Nullable VirtualFile toSelect,
    @NotNull Consumer<? super List<VirtualFile>> callback
  ) {
    var parentComponent = parent == null ? WindowManager.getInstance().suggestParentWindow(project) : parent;
    FileChooserFactory.getInstance().createPathChooser(descriptor, project, parentComponent).choose(toSelect, callback);
  }

  /**
   * Shows file/folder open dialog, allows user to choose file/folder and then passes the result to callback on EDT.
   *
   * @param descriptor file chooser descriptor
   * @param project    project
   * @param toSelect   file to preselect
   * @param callback   invoked after user closes dialog, and only if there is a selected file
   */
  public static void chooseFile(
    @NotNull FileChooserDescriptor descriptor,
    @Nullable Project project,
    @Nullable VirtualFile toSelect,
    @NotNull Consumer<? super VirtualFile> callback
  ) {
    chooseFile(descriptor, project, null, toSelect, callback);
  }

  /**
   * Shows file/folder open dialog, allows user to choose file/folder and then passes the result to callback on EDT.
   *
   * @param descriptor file chooser descriptor
   * @param project    project
   * @param parent     parent component
   * @param toSelect   file to preselect
   * @param callback   invoked after user closes dialog, and only if there is a selected file
   */
  public static void chooseFile(
    @NotNull FileChooserDescriptor descriptor,
    @Nullable Project project,
    @Nullable Component parent,
    @Nullable VirtualFile toSelect,
    @NotNull Consumer<? super VirtualFile> callback
  ) {
    LOG.assertTrue(!descriptor.isChooseMultiple());
    chooseFiles(descriptor, project, parent, toSelect, files -> callback.consume(files.get(0)));
  }
}
