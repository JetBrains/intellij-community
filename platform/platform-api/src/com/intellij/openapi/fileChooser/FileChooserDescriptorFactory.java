/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.ui.UIBundle;

public class FileChooserDescriptorFactory {
  private FileChooserDescriptorFactory() {}

  public static FileChooserDescriptor createAllButJarContentsDescriptor() {
    return new FileChooserDescriptor(true, true, true, true, false, true);
  }

  public static FileChooserDescriptor createMultipleFilesNoJarsDescriptor() {
    return new FileChooserDescriptor(true, false, false, false, false, true);
  }

  public static FileChooserDescriptor createMultipleFoldersDescriptor() {
    return new FileChooserDescriptor(false, true, false, false, false, true);
  }

  public static FileChooserDescriptor createSingleFileNoJarsDescriptor() {
    return new FileChooserDescriptor(true, false, false, false, false, false);
  }

  public static FileChooserDescriptor createSingleLocalFileDescriptor() {
    return new FileChooserDescriptor(true, true, true, true, false, false);
  }

  public static FileChooserDescriptor createSingleFolderDescriptor() {
    return new FileChooserDescriptor(false, true, false, false, false, false);
  }

  public static FileChooserDescriptor createMultipleJavaPathDescriptor() {
    return new FileChooserDescriptor(false, true, true, false, true, true);
  }

  public static FileChooserDescriptor getDirectoryChooserDescriptor(String aSearchedObjectName) {
    final FileChooserDescriptor singleFolderDescriptor = createSingleFolderDescriptor();
    singleFolderDescriptor.setTitle(UIBundle.message("file.chooser.select.object.title", aSearchedObjectName));
    return singleFolderDescriptor;
  }

  public static FileChooserDescriptor getFileChooserDescriptor(String aSearchedObjectName) {
    final FileChooserDescriptor fileChooserDescriptor = createSingleFileNoJarsDescriptor();
    fileChooserDescriptor.setTitle(UIBundle.message("file.chooser.select.object.title", aSearchedObjectName));
    return fileChooserDescriptor;
  }
}
