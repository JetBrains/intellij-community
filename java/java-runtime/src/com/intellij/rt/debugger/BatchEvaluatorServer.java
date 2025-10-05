// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author Eugene Zhuravlev
 */
package com.intellij.rt.debugger;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@SuppressWarnings("unused")
public final class BatchEvaluatorServer {
  /**
   * Serialize the result in one String to avoid multiple getValue commands from the resulting array
   */
  public static String evaluate(Object[] objects) throws IOException {
    ByteArrayOutputStream bas = new ByteArrayOutputStream();
    //noinspection IOResourceOpenedButNotSafelyClosed
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

  public static String evaluate1(Object arg1) throws IOException {
    return evaluate(new Object[]{arg1});
  }

  public static String evaluate2(Object arg1, Object arg2) throws IOException {
    return evaluate(new Object[]{arg1, arg2});
  }

  public static String evaluate3(Object arg1, Object arg2, Object arg3) throws IOException {
    return evaluate(new Object[]{arg1, arg2, arg3});
  }

  public static String evaluate4(Object arg1, Object arg2, Object arg3, Object arg4) throws IOException {
    return evaluate(new Object[]{arg1, arg2, arg3, arg4});
  }

  public static String evaluate5(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) throws IOException {
    return evaluate(new Object[]{arg1, arg2, arg3, arg4, arg5});
  }

  public static String evaluate6(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6) throws IOException {
    return evaluate(new Object[]{arg1, arg2, arg3, arg4, arg5, arg6});
  }

  public static String evaluate7(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7)
    throws IOException {
    return evaluate(new Object[]{arg1, arg2, arg3, arg4, arg5, arg6, arg7});
  }

  public static String evaluate8(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8)
    throws IOException {
    return evaluate(new Object[]{arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8});
  }

  public static String evaluate9(Object arg1,
                                 Object arg2,
                                 Object arg3,
                                 Object arg4,
                                 Object arg5,
                                 Object arg6,
                                 Object arg7,
                                 Object arg8,
                                 Object arg9) throws IOException {
    return evaluate(new Object[]{arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9});
  }

  public static String evaluate10(Object arg1,
                                  Object arg2,
                                  Object arg3,
                                  Object arg4,
                                  Object arg5,
                                  Object arg6,
                                  Object arg7,
                                  Object arg8,
                                  Object arg9,
                                  Object arg10) throws IOException {
    return evaluate(new Object[]{arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10});
  }
}
