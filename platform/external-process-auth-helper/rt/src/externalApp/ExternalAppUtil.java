// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package externalApp;

import org.apache.xmlrpc.XmlRpcClientLite;
import org.apache.xmlrpc.XmlRpcException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Vector;

public final class ExternalAppUtil {
  @SuppressWarnings("UseOfObsoleteCollectionType")
  public static <T> T sendXmlRequest(@NotNull String methodName, int port, @NotNull Object @NotNull ... parameters) {
    try {
      XmlRpcClientLite client = new XmlRpcClientLite("127.0.0.1", port);
      Vector<Object> params = new Vector<>(Arrays.asList(parameters));
      return (T)client.execute(methodName, params);
    }
    catch (XmlRpcException | IOException e) {
      throw new RuntimeException("Invocation failed " + e.getMessage(), e);
    }
  }

  /**
   * Since XML RPC client does not understand null values, the value should be adjusted.
   * Prepend non-null values with "+", replace null values with "-".
   */
  @NotNull
  public static String adjustNullTo(@Nullable String s) {
    return s == null ? "-" : "+" + s;
  }

  @Nullable
  public static String adjustNullFrom(@NotNull String s) {
    return s.charAt(0) == '-' ? null : s.substring(1);
  }

  @NotNull
  public static String getEnv(@NotNull String env) {
    String value = System.getenv(env);
    if (value == null) {
      throw new IllegalStateException(env + " environment variable is not defined!");
    }
    return value;
  }

  public static int getEnvInt(@NotNull String env) {
    return Integer.parseInt(getEnv(env));
  }
}
