/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class FileChooser {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileChooser.FileChooser");

  private FileChooser() { }

  @NotNull
  public static VirtualFile[] chooseFiles(Project project, FileChooserDescriptor descriptor) {
    return chooseFiles(project, descriptor, null);
  }

  @NotNull
  public static VirtualFile[] chooseFiles(Component parent, FileChooserDescriptor descriptor) {
    return chooseFiles(parent, descriptor, null);
  }

  @NotNull
  public static VirtualFile[] chooseFiles(Project project, FileChooserDescriptor descriptor, @Nullable VirtualFile toSelect) {
    FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project);
    return chooser.choose(toSelect, project);
  }

  @NotNull
  public static VirtualFile[] chooseFiles(Component parent, FileChooserDescriptor descriptor, @Nullable VirtualFile toSelect) {
    FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, parent);
    return chooser.choose(toSelect, null);
  }

  @Nullable
  public static VirtualFile chooseFile(Project project, FileChooserDescriptor descriptor) {
    return chooseFile(project, descriptor, null);
  }

  @Nullable
  public static VirtualFile chooseFile(Component parent, FileChooserDescriptor descriptor) {
    return chooseFile(parent, descriptor, null);
  }

  @Nullable
  public static VirtualFile chooseFile(Project project, FileChooserDescriptor descriptor, @Nullable VirtualFile toSelect) {
    LOG.assertTrue(!descriptor.isChooseMultiple());
    return ArrayUtil.getFirstElement(chooseFiles(project, descriptor, toSelect));
  }

  @Nullable
  public static VirtualFile chooseFile(Component parent, FileChooserDescriptor descriptor, @Nullable VirtualFile toSelect) {
    LOG.assertTrue(!descriptor.isChooseMultiple());
    return ArrayUtil.getFirstElement(chooseFiles(parent, descriptor, toSelect));
  }

  /**
   * Shows file/folder open dialog, allows user to choose files/folders and then passes result to callback in EDT.
   * On MacOS Open Dialog will be shown with slide effect if Macish UI is turned on.
   *
   * @param descriptor file chooser descriptor
   * @param project    project
   * @param toSelect   file to preselect
   * @param callback   callback will be invoked after user have closed dialog
   */
  public static void chooseFilesWithSlideEffect(@NotNull final FileChooserDescriptor descriptor,
                                                @Nullable final Project project,
                                                @Nullable final VirtualFile toSelect,
                                                @NotNull final Consumer<VirtualFile[]> callback) {
    chooseFilesWithSlideEffect(descriptor, project, null, toSelect, callback);
  }

  /**
   * Shows file/folder open dialog, allows user to choose files/folders and then passes result to callback in EDT.
   * On MacOS Open Dialog will be shown with slide effect if Macish UI is turned on.
   *
   * @param descriptor file chooser descriptor
   * @param project    project
   * @param parent     parent component
   * @param toSelect   file to preselect
   * @param callback   callback will be invoked after user have closed dialog
   */
  public static void chooseFilesWithSlideEffect(@NotNull final FileChooserDescriptor descriptor,
                                                @Nullable final Project project,
                                                @Nullable final Component parent,
                                                @Nullable final VirtualFile toSelect,
                                                @NotNull final Consumer<VirtualFile[]> callback) {
    if (useNativeMacChooser(descriptor)) {
      descriptor.putUserData(MacFileChooserDialog.NATIVE_MAC_FILE_CHOOSER_ENABLED, Boolean.TRUE);
    }

    final FileChooserFactory factory = FileChooserFactory.getInstance();
    final FileChooserDialog dialog = parent != null ? factory.createFileChooser(descriptor, parent)
                                                    : factory.createFileChooser(descriptor, project);

    if (dialog instanceof MacFileChooserDialog) {
      ((MacFileChooserDialog)dialog).chooseWithSheet(toSelect, project, new MacFileChooserDialog.MacFileChooserCallback() {
        public void onChosen(@NotNull final VirtualFile[] files) {
          callback.consume(files);
        }
      });
    }
    else {
      final VirtualFile[] files = dialog.choose(toSelect, project);
      callback.consume(files);
    }
  }

  private static boolean useNativeMacChooser(final FileChooserDescriptor descriptor) {
    @SuppressWarnings("deprecation") final boolean nativeMacChooserEnabled = isNativeMacChooserEnabled();
    return SystemInfo.isMac && !descriptor.isChooseJarContents() && nativeMacChooserEnabled;
  }

  /** @deprecated internal method, to make private in IDEA 12 */
  public static boolean isNativeMacChooserEnabled() {
    return "true".equalsIgnoreCase(System.getProperty("native.mac.file.chooser.enabled", "true")) &&
           Registry.is("ide.mac.filechooser.native");
  }
}