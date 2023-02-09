// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

public final class TargetEnvironmentUtil {
  private static final Logger LOG = Logger.getInstance(TargetEnvironmentUtil.class);

  /**
   * Tries to upload a file that was already uploaded as a root during target preparation.
   *
   * @return if afterUploadCallback would be invoked later, after uploading the file.
   */
  public static boolean reuploadRootFile(@NotNull File file,
                                         @Nullable TargetEnvironmentRequest request,
                                         @NotNull TargetEnvironment remoteEnvironment,
                                         @Nullable TargetProgressIndicator indicator,
                                         @Nullable Runnable afterUploadCallback) {
    if (request == null || request instanceof LocalTargetEnvironmentRequest) {
      return false;
    }
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      Path parentPath = file.toPath().getParent();
      for (TargetEnvironment.UploadRoot uploadRoot : request.getUploadVolumes()) {
        if (parentPath.equals(uploadRoot.getLocalRootPath())) {
          TargetProgressIndicator targetProgressIndicator = Objects.requireNonNull(indicator);
          try {
            remoteEnvironment.getUploadVolumes().get(uploadRoot).upload(file.getName(), targetProgressIndicator);
          }
          catch (Throwable t) {
            LOG.warn(t);
            targetProgressIndicator.addSystemLine("");
            targetProgressIndicator.stopWithErrorMessage(
              ExecutionBundle.message("dialog.message.failed.to.reupload", file.getName(), StringUtil.notNullize(t.getLocalizedMessage())));
          }

          if (afterUploadCallback != null) {
            afterUploadCallback.run();
          }
          return;
        }
      }
      LOG.error("Did not find upload volume for " + parentPath);
      if (afterUploadCallback != null) {
        afterUploadCallback.run();
      }
    });
    return true;
  }
}
