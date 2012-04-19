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
package com.intellij.openapi.diff;

import com.intellij.ide.diff.DiffElement;
import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ApplicationStarterEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.io.File;

/**
 * @author max
 * @author Konstantin Bulenkov
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public class DiffApplication implements ApplicationStarterEx {
  public String getCommandName() {
    return "diff";
  }

  public void premain(String[] args) {
    if (args.length != 3) {
      printHelp();
    }
  }

  private static void printHelp() {
    System.err.println(DiffBundle.message("diff.application.usage.parameters.and.description"));
    System.exit(1);
  }

  public void main(String[] args) {
    try {
      processDiffCommand(args);
    }
    catch (OperationFailedException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }
    catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  @Override
  public void processExternalCommandLine(String[] args) {
    if (args.length != 3) {
      String productName = ApplicationNamesInfo.getInstance().getProductName();
      Messages.showMessageDialog("Usage: " + productName.toLowerCase() + " diff <file1> <file2>",
                                 "Diff", Messages.getInformationIcon());
      return;
    }
    try {
      processDiffCommand(args);
    }
    catch(Exception e) {
      Messages.showMessageDialog("Error showing diff: " + e.getMessage(), "Diff", Messages.getErrorIcon());
    }
  }

  private static void processDiffCommand(String[] args) throws OperationFailedException {
    final String path1 = args[1];
    final String path2 = args[2];
    final VirtualFile file1 = findFile(path1);
    final VirtualFile file2 = findFile(path2);
    final boolean areDirs = areDirs(file1, file2);
    final boolean areJars = areJars(file1, file2);
    if (areDirs || areJars) {
      final DirDiffManager diffManager = DirDiffManager.getInstance(ProjectManager.getInstance().getDefaultProject());
      final DiffElement d1 = diffManager.createDiffElement(file1);
      final DiffElement d2 = diffManager.createDiffElement(file2);
      if (d1 == null) {
        throw new OperationFailedException("cannot.create.diff.error", path1);
      }
      if (d2 == null) {
        throw new OperationFailedException("cannot.create.diff.error", path1);
      }
      else if (!diffManager.canShow(d1, d2)) {
        throw new OperationFailedException("cannot.compare.error", path1, path2);
      }

      final DirDiffSettings settings = new DirDiffSettings();
      settings.showInFrame = false;
      diffManager.showDiff(d1, d2, settings, null);
    }
    else {
      file1.refresh(false, false);
      file2.refresh(false, false);

      if (file1.getFileType() == UnknownFileType.INSTANCE) {
        throw new OperationFailedException("unknown.file.type.error", path1);
      }
      else if (file2.getFileType() == UnknownFileType.INSTANCE) {
        throw new OperationFailedException("unknown.file.type.error", path2);
      }

      SimpleDiffRequest request = SimpleDiffRequest.compareFiles(file1, file2, ProjectManager.getInstance().getDefaultProject());
      request.addHint(DiffTool.HINT_SHOW_MODAL_DIALOG);
      DiffManager.getInstance().getIdeaDiffTool().show(request);
      FileDocumentManager.getInstance().saveAllDocuments();
    }
  }

  private static boolean areJars(VirtualFile file1, VirtualFile file2) {
    return JarFileSystem.PROTOCOL.equalsIgnoreCase(file1.getExtension()) && JarFileSystem.PROTOCOL.equalsIgnoreCase(file2.getExtension());
  }

  private static boolean areDirs(VirtualFile file1, VirtualFile file2) {
    return file1.isDirectory() && file2.isDirectory();
  }

  @NotNull
  private static VirtualFile findFile(final String path) throws OperationFailedException {
    final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(path));
    if (file == null) {
      throw new OperationFailedException("cannot.file.file.error", path);
    }
    return file;
  }

  private static class OperationFailedException extends Exception {
    public OperationFailedException(@NotNull @PropertyKey(resourceBundle = "messages.DiffBundle") String key, Object... params) {
      super(DiffBundle.message(key, params));
    }
  }
}
