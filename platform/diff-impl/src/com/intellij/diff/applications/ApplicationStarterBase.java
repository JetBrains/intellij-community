/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.applications;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationStarterEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public abstract class ApplicationStarterBase extends ApplicationStarterEx {
  protected static final Logger LOG = Logger.getInstance(ApplicationStarterBase.class);

  protected abstract boolean checkArguments(@NotNull String[] args);

  @NotNull
  protected abstract String getUsageMessage();

  protected abstract void processCommand(@NotNull String[] args, @Nullable String currentDirectory)
    throws Exception;

  //
  // Impl
  //

  @Override
  public boolean isHeadless() {
    return false;
  }

  @Override
  public void processExternalCommandLine(@NotNull String[] args, @Nullable String currentDirectory) {
    if (!checkArguments(args)) {
      Messages.showMessageDialog(getUsageMessage(), StringUtil.toTitleCase(getCommandName()), Messages.getInformationIcon());
      return;
    }
    try {
      processCommand(args, currentDirectory);
    }
    catch (Exception e) {
      Messages.showMessageDialog(String.format("Error showing %s: %s", getCommandName(), e.getMessage()),
                                 StringUtil.toTitleCase(getCommandName()),
                                 Messages.getErrorIcon());
    }
    finally {
      saveAll();
    }
  }

  private static void saveAll() {
    FileDocumentManager.getInstance().saveAllDocuments();
    ApplicationManager.getApplication().saveSettings();
  }

  @Override
  public void premain(String[] args) {
    if (!checkArguments(args)) {
      System.out.println(getUsageMessage());
      System.exit(1);
    }
  }

  @Override
  public void main(String[] args) {
    try {
      processCommand(args, null);
    }
    catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
    catch (Throwable t) {
      t.printStackTrace();
      System.exit(2);
    }
    finally {
      saveAll();
    }

    System.exit(0);
  }

  @NotNull
  public static List<VirtualFile> findFiles(@NotNull List<String> filePaths, @Nullable String currentDirectory) throws Exception {
    List<VirtualFile> files = new ArrayList<>();

    for (String path : filePaths) {
      VirtualFile virtualFile = findFile(path, currentDirectory);
      if (virtualFile == null) throw new Exception("Can't find file " + path);
      files.add(virtualFile);
    }

    VfsUtil.markDirtyAndRefresh(false, false, false, VfsUtilCore.toVirtualFileArray(files));

    for (int i = 0; i < filePaths.size(); i++) {
      if (!files.get(i).isValid()) throw new Exception("Can't find file " + filePaths.get(i));
    }

    return files;
  }

  @Nullable
  public static VirtualFile findFile(@NotNull String path, @Nullable String currentDirectory) {
    File file = getFile(path, currentDirectory);
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    if (virtualFile == null) {
      LOG.warn(String.format("Can't find file: current directory - %s; path - %s", currentDirectory, path));
    }
    return virtualFile;
  }

  @NotNull
  public static File getFile(@NotNull String path, @Nullable String currentDirectory) {
    File file = new File(path);
    if (!file.isAbsolute() && currentDirectory != null) {
      file = new File(currentDirectory, path);
    }
    return file;
  }

  @Override
  public boolean canProcessExternalCommandLine() {
    return true;
  }

  @Nullable
  protected Project getProject() {
    return null; // TODO: try to guess project
  }
}
