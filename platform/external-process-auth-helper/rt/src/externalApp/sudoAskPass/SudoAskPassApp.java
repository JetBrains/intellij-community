// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package externalApp.sudoAskPass;

import externalApp.ExternalApp;
import externalApp.ExternalAppEntry;
import externalApp.ExternalAppUtil;

public class SudoAskPassApp implements ExternalApp {

  private SudoAskPassApp() { }

  public static void main(String[] args) {
    ExternalAppUtil.handleAskPassInvocation(SudoExternalAppHandler.IJ_SUDO_ASK_PASS_HANDLER_ENV,
                                            SudoExternalAppHandler.IJ_SUDO_ASK_PASS_PORT_ENV,
                                            SudoExternalAppHandler.ENTRY_POINT_NAME,
                                            ExternalAppEntry.fromMain(args));
  }
}
