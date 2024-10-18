// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author Eugene Zhuravlev
 */
package com.intellij.rt.debugger;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

public final class BatchEvaluatorServer {
  /**
   * Serialize result in one String to avoid multiple getValue commands from the resulting array
   */
  public static String evaluate(Object[] args) throws IOException {
    // the last element is always null, it is reserved for the return value (to avoid gc collection)
    Object[] objects = Arrays.copyOf(args, args.length - 1);

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
    String result = bas.toString("ISO-8859-1");
    args[args.length - 1] = result; // store the result as the last array element to avoid it being collected
    return result;
  }
}
