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
package com.intellij.codeInspection.export;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class HTMLExportUtil {
  public static void writeFile(final String folder, @NonNls final String fileName, CharSequence buf, final Project project) {
    try {
      HTMLExporter.writeFileImpl(folder, fileName, buf);
    }
    catch (IOException e) {
      Runnable showError = new Runnable() {
        @Override
        public void run() {
          final String fullPath = folder + File.separator + fileName;
          Messages.showMessageDialog(
            project,
            InspectionsBundle.message("inspection.export.error.writing.to", fullPath),
            InspectionsBundle.message("inspection.export.results.error.title"),
            Messages.getErrorIcon()
          );
        }
      };
      ApplicationManager.getApplication().invokeLater(showError, ModalityState.NON_MODAL);
      throw new ProcessCanceledException();
    }
  }

  public static void runExport(final Project project, @NotNull ThrowableRunnable<IOException> runnable) {
    try {
      runnable.run();
    }
    catch (IOException e) {
      Runnable showError = new Runnable() {
        @Override
        public void run() {
          Messages.showMessageDialog(
            project,
            InspectionsBundle.message("inspection.export.error.writing.to", "export file"),
            InspectionsBundle.message("inspection.export.results.error.title"),
            Messages.getErrorIcon()
          );
        }
      };
      ApplicationManager.getApplication().invokeLater(showError, ModalityState.NON_MODAL);
      throw new ProcessCanceledException();
    }
  }
}
