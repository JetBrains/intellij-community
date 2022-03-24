// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package externalApp.nativessh;


import externalApp.ExternalApp;
import externalApp.ExternalAppUtil;

/**
 * <p>This is a program that would be called by ssh when key passphrase is needed,
 * and if {@code SSH_ASKPASS} variable is set to the script that invokes this program.</p>
 * <p>ssh expects the reply from the program's standard output.</p>
 */
public class NativeSshAskPassApp implements ExternalApp {

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void main(String[] args) {
    try {
      String description;
      if (args.length > 0) {
        description = args[0];
      }
      else {
        description = ""; // XML RPC doesn't like nulls
      }

      String handlerNo = ExternalAppUtil.getEnv(NativeSshAskPassXmlRpcHandler.IJ_SSH_ASK_PASS_HANDLER_ENV);
      int xmlRpcPort = ExternalAppUtil.getEnvInt(NativeSshAskPassXmlRpcHandler.IJ_SSH_ASK_PASS_PORT_ENV);

      String response = ExternalAppUtil.sendXmlRequest(NativeSshAskPassXmlRpcHandler.RPC_METHOD_NAME, xmlRpcPort,
                                                       handlerNo, description);
      String passphrase = ExternalAppUtil.adjustNullFrom(response);
      if (passphrase == null) {
        System.exit(1); // dialog canceled
      }

      System.out.println(passphrase);
    }
    catch (Throwable t) {
      System.err.println(t.getMessage());
      t.printStackTrace(System.err);
      System.exit(1);
    }
  }
}
