// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author: Eugene Zhuravlev
 */
package com.intellij.rt.debugger;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class BatchEvaluatorServer {
  /**
   * Serialize result in one String to avoid multiple getValue commands from the resulting array
   */
  public static String evaluate(Object[] objects) throws IOException {
    ByteArrayOutputStream bas = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(bas);
    for (Object object : objects) {
      String res;
      boolean error = false;
      try {
        res = object.toString();
        if (res == null) {
          res = "null";
        }
      }
      catch (Throwable e) {
        res = e.getClass().getName();
        error = true;
      }

      dos.writeBoolean(error);
      dos.writeUTF(res);
    }
    return bas.toString("ISO-8859-1");
  }
}
