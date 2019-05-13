/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.ide;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public abstract class CustomPortServerManager {
  public static final ExtensionPointName<CustomPortServerManager> EP_NAME = ExtensionPointName.create("org.jetbrains.customPortServerManager");

  public abstract void cannotBind(Exception e, int port);

  public interface CustomPortService {
    boolean rebind();

    boolean isBound();
  }

  public abstract int getPort();

  public abstract boolean isAvailableExternally();

  public abstract void setManager(@Nullable CustomPortService manager);

  /**
   * This server will accept only XML-RPC requests if this method returns not-null map of XMl-RPC handlers
   */
  @Nullable
  public Map<String, Object> createXmlRpcHandlers() {
    return null;
  }
}