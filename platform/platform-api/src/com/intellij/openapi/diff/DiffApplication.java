/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ApplicationStarterEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * @author max
 */
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
    catch (FileNotFoundException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    finally {
      System.exit(0);
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

  private static void processDiffCommand(String[] args) throws FileNotFoundException {
    String path1 = args[1];
    String path2 = args[2];
    VirtualFile file1 = findFile(path1);
    VirtualFile file2 = findFile(path2);
    SimpleDiffRequest request = SimpleDiffRequest.compareFiles(file1, file2, null);
    request.addHint(DiffTool.HINT_SHOW_MODAL_DIALOG);
    DiffManager.getInstance().getIdeaDiffTool().show(request);
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  private static VirtualFile findFile(final String path1) throws FileNotFoundException {
    final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(path1));
    if (vFile == null) {
      throw new FileNotFoundException(DiffBundle.message("cannot.file.file.error.message", path1));
    }
    return vFile;
  }
}
