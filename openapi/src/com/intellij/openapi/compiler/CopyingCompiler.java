/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.compiler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.IOUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class CopyingCompiler implements PackagingCompiler{
  public abstract VirtualFile[] getFilesToCopy(CompileContext context);
  public abstract String getDestinationPath(VirtualFile sourceFile);

  public final void processOutdatedItem(CompileContext context, String url, ValidityState state) {
    final String destinationPath = ((DestinationFileInfo)state).getDestinationPath();
    new File(destinationPath).delete();
  }

  public final ProcessingItem[] getProcessingItems(final CompileContext context) {
    return ApplicationManager.getApplication().runReadAction(new Computable<ProcessingItem[]>() {
      public ProcessingItem[] compute() {
        final VirtualFile[] filesToCopy = getFilesToCopy(context);
        final ProcessingItem[] items = new ProcessingItem[filesToCopy.length];
        for (int idx = 0; idx < filesToCopy.length; idx++) {
          final VirtualFile file = filesToCopy[idx];
          items[idx] = new CopyItem(file, getDestinationPath(file));
        }
        return items;
      }
    });
  }

  public ProcessingItem[] process(CompileContext context, ProcessingItem[] items) {
    final List<ProcessingItem> successfullyProcessed = new ArrayList<ProcessingItem>(items.length);
    for (int idx = 0; idx < items.length; idx++) {
      final CopyItem item = (CopyItem)items[idx];
      final String fromPath = item.getSourcePath();
      final String toPath = item.getDestinationPath();
      try {
        FileUtil.copy(new File(fromPath), new File(toPath));
        successfullyProcessed.add(item);
      }
      catch (IOException e) {
        context.addMessage(
          CompilerMessageCategory.ERROR,
          "Error copying " + fromPath + "\nto " + toPath + ":\n" + e.getMessage(),
          null, -1, -1
        );
      }
    }
    return successfullyProcessed.toArray(new ProcessingItem[successfullyProcessed.size()]);
  }

  public String getDescription() {
    return "File copying compiler";
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  public ValidityState createValidityState(DataInputStream is) throws IOException {
    return new DestinationFileInfo(IOUtil.readString(is), true);
  }

  private static class CopyItem implements FileProcessingCompiler.ProcessingItem {
    private final VirtualFile myFile;
    private final DestinationFileInfo myInfo;
    private final String mySourcePath;

    public CopyItem(VirtualFile file, String destinationPath) {
      myFile = file;
      mySourcePath = file.getPath().replace('/', File.separatorChar);
      myInfo = new DestinationFileInfo(destinationPath, new File(destinationPath).exists());
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public ValidityState getValidityState() {
      return myInfo;
    }

    public String getSourcePath() {
      return mySourcePath;
    }

    public String getDestinationPath() {
      return myInfo.getDestinationPath();
    }
  }

  private static class DestinationFileInfo implements ValidityState {
    private final String destinationPath;
    private final boolean myFileExists;

    public DestinationFileInfo(String destinationPath, boolean fileExists) {
      this.destinationPath = destinationPath;
      myFileExists = fileExists;
    }

    public boolean equalsTo(ValidityState otherState) {
      if (!(otherState instanceof DestinationFileInfo)) {
        return false;
      }
      DestinationFileInfo destinationFileInfo = (DestinationFileInfo)otherState;
      return (myFileExists == destinationFileInfo.myFileExists) && (destinationPath.equals(destinationFileInfo.destinationPath));
    }

    public void save(DataOutputStream os) throws IOException {
      IOUtil.writeString(destinationPath, os);
    }

    public String getDestinationPath() {
      return destinationPath;
    }
  }

}
