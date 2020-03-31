/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger;

import com.intellij.DynamicBundle;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public class JavaDebuggerBundle extends DynamicBundle {
  @NonNls private static final String BUNDLE = "messages.JavaDebuggerBundle";
  private static final JavaDebuggerBundle INSTANCE = new JavaDebuggerBundle();

  private JavaDebuggerBundle() { super(BUNDLE); }

  @NotNull
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  @NotNull
  public static Supplier<String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }

  public static String getAddressDisplayName(final RemoteConnection connection) {
    return connection.isUseSockets() ? StringUtil.notNullize(connection.getDebuggerHostName()) + ":" + connection.getDebuggerAddress()
                                     : connection.getDebuggerAddress();
  }

  public static String getTransportName(final RemoteConnection connection) {
    return connection.isUseSockets() ? message("transport.name.socket") : message("transport.name.shared.memory");
  }
}