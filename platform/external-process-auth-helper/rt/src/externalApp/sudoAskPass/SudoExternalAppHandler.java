// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package externalApp.sudoAskPass;

import externalApp.ExternalAppHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SudoExternalAppHandler extends ExternalAppHandler {
  @NonNls String IJ_SUDO_ASK_PASS_HANDLER_ENV = "INTELLIJ_SUDO_ASKPASS_HANDLER";
  @NonNls String IJ_SUDO_ASK_PASS_PORT_ENV = "INTELLIJ_SUDO_ASKPASS_PORT";
  @NonNls String ENTRY_POINT_NAME = "sudoAskPass";

  @Nullable
  String handleInput(@NotNull String description);
}
