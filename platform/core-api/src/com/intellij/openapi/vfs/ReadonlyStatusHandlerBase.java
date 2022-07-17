// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.core.CoreBundle;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.Strings;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ReadonlyStatusHandlerBase extends ReadonlyStatusHandler {

  private static final Logger LOG = Logger.getInstance(ReadonlyStatusHandlerBase.class);

  private final Project myProject;

  public ReadonlyStatusHandlerBase(Project project) {
    myProject = project;
  }

  private static void checkThreading() {
    Application app = ApplicationManager.getApplication();
    app.assertIsWriteThread();
    if (!app.isWriteAccessAllowed()) return;

    if (app.isUnitTestMode() && Registry.is("tests.assert.clear.read.only.status.outside.write.action")) {
      LOG.error("ensureFilesWritable should be called outside write action");
    }
  }

  protected static OperationStatus createResultStatus(@NotNull Collection<? extends VirtualFile> originalFiles,
                                                      @NotNull Collection<? extends VirtualFile> files) {
    List<VirtualFile> readOnlyFiles = new ArrayList<>();
    for (VirtualFile file : files) {
      if (file.exists()) {
        if (!file.isWritable()) {
          readOnlyFiles.add(file);
        }
      }
    }

    // we shouldn't report success if files for which write operation is requested are still non-writable
    assert !readOnlyFiles.isEmpty() || ContainerUtil.and(originalFiles, file -> file == null || file.isWritable())
      : "Original files: " + originalFiles + ", files: " + files;

    return new OperationStatusImpl(VfsUtilCore.toVirtualFileArray(readOnlyFiles));
  }

  @NotNull
  @Override
  public OperationStatus ensureFilesWritable(@NotNull Collection<? extends VirtualFile> originalFiles) {
    if (originalFiles.isEmpty()) {
      return new OperationStatusImpl(VirtualFile.EMPTY_ARRAY);
    }

    checkThreading();

    Set<VirtualFile> realFiles = new HashSet<>(originalFiles.size());
    for (VirtualFile file : originalFiles) {
      if (file instanceof LightVirtualFile) {
        VirtualFile originalFile = ((LightVirtualFile)file).getOriginalFile();
        if (originalFile != null) {
          file = originalFile;
        }
      }
      if (file instanceof VirtualFileWindow) {
        file = ((VirtualFileWindow)file).getDelegate();
      }
      if (file instanceof BackedVirtualFile) {
        file = ((BackedVirtualFile)file).getOriginFile();
      }
      if (file != null) {
        realFiles.add(file);
      }
    }
    Collection<? extends VirtualFile> files = new ArrayList<>(realFiles);

    if (!myProject.isDefault()) {
      OperationStatusImpl status = WritingAccessProvider.EP.computeSafeIfAny(myProject, provider -> {
        Collection<VirtualFile> denied = ContainerUtil.filter(files, virtualFile -> !provider.isPotentiallyWritable(virtualFile));

        if (denied.isEmpty()) {
          denied = provider.requestWriting(files);
        }
        if (!denied.isEmpty()) {
          return new OperationStatusImpl(VfsUtilCore.toVirtualFileArray(denied), provider.getReadOnlyMessage());
        }
        return null;
      });
      if (status != null) {
        return status;
      }
    }

    return ensureFilesWritable(originalFiles, files);
  }

  @NotNull
  protected OperationStatus ensureFilesWritable(@NotNull Collection<? extends VirtualFile> originalFiles,
                                                Collection<? extends VirtualFile> files) {
    return createResultStatus(originalFiles, files);
  }


  public static final class OperationStatusImpl extends OperationStatus {
    private final VirtualFile[] myReadonlyFiles;
    @NotNull private final @NlsContexts.DialogMessage String myReadOnlyReason;

    public OperationStatusImpl(VirtualFile @NotNull [] readonlyFiles) {
      this(readonlyFiles, "");
    }

    private OperationStatusImpl(VirtualFile[] readonlyFiles, @NotNull @NlsContexts.DialogMessage String readOnlyReason) {
      myReadonlyFiles = readonlyFiles;
      myReadOnlyReason = readOnlyReason;
    }

    @Override
    public VirtualFile @NotNull [] getReadonlyFiles() {
      return myReadonlyFiles;
    }

    @Override
    public boolean hasReadonlyFiles() {
      return myReadonlyFiles.length > 0;
    }

    @Override
    @NotNull
    public String getReadonlyFilesMessage() {
      if (hasReadonlyFiles()) {
        if (!Strings.isEmpty(myReadOnlyReason)) {
          return myReadOnlyReason;
        }
        if (myReadonlyFiles.length > 1) {
          StringBuilder buf = new StringBuilder();
          for (VirtualFile file : myReadonlyFiles) {
            buf.append('\n');
            buf.append(file.getPresentableUrl());
          }

          return CoreBundle.message("failed.to.make.the.following.files.writable.error.message", buf.toString());
        }
        else {
          return CoreBundle.message("failed.to.make.file.writable.error.message", myReadonlyFiles[0].getPresentableUrl());
        }
      }
      throw new RuntimeException("No readonly files");
    }
  }
}
