/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.CommonBundle;
import com.intellij.execution.configurations.RemoteConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

public class DebuggerBundle {
  @NonNls private static final String BUNDLE = "messages.DebuggerBundle";

  private DebuggerBundle() {
  }

  public static String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
    return CommonBundle.message(ResourceBundle.getBundle(BUNDLE), key, params);
  }

  public static String getAddressDisplayName(final RemoteConnection connection) {
    return connection.isUseSockets()? connection.getHostName() + ":" + connection.getAddress() : connection.getAddress();
  }

  public static String getTransportName(final RemoteConnection connection) {
    return connection.isUseSockets() ? message("transport.name.socket") : message("transport.name.shared.memory");
  }
}
