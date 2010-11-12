/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.execution.util;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;

import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.HashSet;

/**
 * HgSetExecutablePathPanel is a {@link com.intellij.openapi.ui.TextFieldWithBrowseButton}, which opens a file chooser
 * to set path for executable and checks the validity of the selected file to be this executable.
 * and checks validity of the selected file to be an executable.
 */
class ExecutablePathPanel extends TextFieldWithBrowseButton {

  private final Collection<ActionListener> myOkListeners = new HashSet<ActionListener>();

  ExecutablePathPanel(final ExecutableValidator executableValidator) {
    setText(executableValidator.getCurrentExecutable());
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        String path = file.getPath();
        if (!executableValidator.isExecutableValid(path)) {
          return false;
        }
        for (ActionListener okListener : myOkListeners) {
          okListener.actionPerformed(null);
        }
        return true;
      }
    };
    addBrowseFolderListener(executableValidator.getFileChooserTitle(), executableValidator.getFileChooserDescription(), null, descriptor);
  }

  /**
   * Adds a listener which will be called when file chooser dialog is closed successfully.
   */
  void addOKListener(ActionListener listener) {
    myOkListeners.add(listener);
  }

}
