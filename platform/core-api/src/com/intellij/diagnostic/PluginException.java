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
package com.intellij.diagnostic;

import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NonNls;

/**
 * @author stathik
 * @since Jan 8, 2004
 */
public class PluginException extends RuntimeException {
  private final PluginId myPluginId;

  public PluginException(String message, Throwable cause, PluginId pluginId) {
    super(message, cause);
    myPluginId = pluginId;
  }

  public PluginException(Throwable e, PluginId pluginId) {
    super (e.getMessage(), e);
    myPluginId = pluginId;
  }

  public PluginException(final String message, final PluginId pluginId) {
    super(message);
    myPluginId = pluginId;
  }

  public PluginId getPluginId() {
    return myPluginId;
  }

  @Override
  public String getMessage() {
    @NonNls String message = super.getMessage();

    if (message == null) {
      message = "";
    }

    message += " [Plugin: " + myPluginId.toString() + "]";
    return message;
  }
}
