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
package com.intellij.openapi.roots.libraries.doc;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author Rustam Vishnyakov
 */
public class DocUrlChooser {

  private DocUrlChooser() {
  }

  @Nullable
  public static VirtualFile showSpecifyDocUrlDialog(JComponent parent) {
    return showSpecifyDocUrlDialog(parent, "");
  }

  @Nullable
  public static VirtualFile showSpecifyDocUrlDialog(JComponent parent, String initialValue) {
    final String url = Messages.showInputDialog(parent, ProjectBundle.message("sdk.configure.javadoc.url.prompt"),
                                                ProjectBundle.message("sdk.configure.javadoc.url.title"), Messages.getQuestionIcon(),
                                                initialValue, new InputValidator() {
        public boolean checkInput(String inputString) {
          return true;
        }

        public boolean canClose(String inputString) {
          try {
            new URL(inputString);
            return true;
          }
          catch (MalformedURLException e1) {
            Messages.showErrorDialog(e1.getMessage(), ProjectBundle.message("sdk.configure.javadoc.url.title"));
          }
          return false;
        }
      });
    if (url == null) {
      return null;
    }
    return VirtualFileManager.getInstance().findFileByUrl(url);
  }
}
