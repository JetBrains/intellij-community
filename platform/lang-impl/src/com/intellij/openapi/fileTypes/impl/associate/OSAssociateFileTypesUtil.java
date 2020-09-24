// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl.associate;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.impl.associate.ui.FileTypeAssociationDialog;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class OSAssociateFileTypesUtil {
  public final static String ENABLE_REG_KEY =  "system.file.type.associations.enabled";

  private static final Logger LOG = Logger.getInstance(OSAssociateFileTypesUtil.class);

  private OSAssociateFileTypesUtil() {
  }

  public static void chooseAndAssociate(@NotNull Callback callback) {
    SystemFileTypeAssociator associator = SystemAssociatorFactory.getAssociator();
    if (associator != null) {
      FileTypeAssociationDialog dialog = new FileTypeAssociationDialog();
      if (dialog.showAndGet()) {
        ApplicationManager.getApplication().executeOnPooledThread(
          () -> {
            try {
              callback.beforeStart();
              associator.associateFileTypes(dialog.getSelectedFileTypes());
              callback.onSuccess(associator.isOsRestartRequired());
            }
            catch (OSFileAssociationException exception) {
              callback.onFailure(exception.getMessage());
              LOG.info(exception);
            }
          }
        );
      }
    }
  }


  public static boolean isAvailable() {
    return Registry.get(ENABLE_REG_KEY).asBoolean() && SystemAssociatorFactory.getAssociator() != null;
  }

  public interface Callback {
    void beforeStart();

    void onSuccess(boolean isOsRestartRequired);

    void onFailure(@NotNull @Nls String errorMessage);
  }
}
