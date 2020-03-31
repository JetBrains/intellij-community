// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author: Eugene Zhuravlev
 */
package com.intellij.rt.debugger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class BatchEvaluatorServer {
  /**
   * Serialize result in one String to avoid multiple getValue commands from the resulting array
   */
  public static String evaluate(Object[] objects) throws IOException {
    ByteArrayOutputStream bas = new ByteArrayOutputStream();
    for (int idx = 0; idx < objects.length; idx++) {
      String res;
      int length;
      try {
        res = objects[idx].toString();
        length = res.length();
      }
      catch (Throwable e) {
        res = e.toString();
        length = -res.length();
      }

      // negative indicates an error
      bas.write(length >>> 24);
      bas.write(length >>> 16);
      bas.write(length >>> 8);
      bas.write(length);

      bas.write(res.getBytes("ISO-8859-1"));
    }
    return bas.toString("ISO-8859-1");
  }
}
