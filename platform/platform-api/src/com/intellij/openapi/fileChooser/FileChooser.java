/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

public class FileChooser {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileChooser.FileChooser");

  /**
   * Normally, callback isn't invoked if a chooser was cancelled.
   * If the situation should be handled separately this interface may be used.
   */
  public interface FileChooserConsumer extends Consumer<List<VirtualFile>> {
    void cancelled();
  }

  private FileChooser() { }

  @NotNull
  public static VirtualFile[] chooseFiles(@NotNull final FileChooserDescriptor descriptor,
                                          @Nullable final Project project,
                                          @Nullable final VirtualFile toSelect) {
    return chooseFiles(descriptor, null, project, toSelect);
  }

  @NotNull
  public static VirtualFile[] chooseFiles(@NotNull final FileChooserDescriptor descriptor,
                                          @Nullable final Component parent,
                                          @Nullable final Project project,
                                          @Nullable final VirtualFile toSelect) {
    final FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, parent);
    return chooser.choose(project, toSelect);
  }

  @Nullable
  public static VirtualFile chooseFile(@NotNull final FileChooserDescriptor descriptor,
                                       @Nullable final Project project,
                                       @Nullable final VirtualFile toSelect) {
    return chooseFile(descriptor, null, project, toSelect);
  }

  @Nullable
  public static VirtualFile chooseFile(@NotNull final FileChooserDescriptor descriptor,
                                       @Nullable final Component parent,
                                       @Nullable final Project project,
                                       @Nullable final VirtualFile toSelect) {
    Component parentComponent = (parent == null) ? KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() : parent;
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
   * @param callback   callback will be invoked after user have closed dialog and only if there are files selected
   * @see FileChooserConsumer
   * @since 11.1
   */
  public static void chooseFiles(@NotNull final FileChooserDescriptor descriptor,
                                 @Nullable final Project project,
                                 @Nullable final VirtualFile toSelect,
                                 @NotNull final Consumer<List<VirtualFile>> callback) {
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
   * @param callback   callback will be invoked after user have closed dialog and only if there are files selected
   * @see FileChooserConsumer
   * @since 11.1
   */
  public static void chooseFiles(@NotNull final FileChooserDescriptor descriptor,
                                 @Nullable final Project project,
                                 @Nullable final Component parent,
                                 @Nullable final VirtualFile toSelect,
                                 @NotNull final Consumer<List<VirtualFile>> callback) {
    Component parentComponent = (parent == null) ? KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() : parent;
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
   * @param callback   callback will be invoked after user have closed dialog and only if there is file selected
   * @since 13
   */
  public static void chooseFile(@NotNull final FileChooserDescriptor descriptor,
                                @Nullable final Project project,
                                @Nullable final VirtualFile toSelect,
                                @NotNull final Consumer<VirtualFile> callback) {
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
   * @param callback   callback will be invoked after user have closed dialog and only if there is file selected
   * @since 13
   */
  public static void chooseFile(@NotNull final FileChooserDescriptor descriptor,
                                @Nullable final Project project,
                                @Nullable final Component parent,
                                @Nullable final VirtualFile toSelect,
                                @NotNull final Consumer<VirtualFile> callback) {
    LOG.assertTrue(!descriptor.isChooseMultiple());
    chooseFiles(descriptor, project, parent, toSelect, files -> callback.consume(files.get(0)));
  }
}
