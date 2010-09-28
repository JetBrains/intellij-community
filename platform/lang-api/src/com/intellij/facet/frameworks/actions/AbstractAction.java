/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.facet.frameworks.actions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;

public abstract class AbstractAction {
  private static final Logger LOG = Logger.getInstance(AbstractAction.class.getName());

  private static String SERVER_URL = Registry.get("frameworks.download.libraries.server.url").asString();
  private Pair<String, String>[] myParams;

  protected AbstractAction(Pair<String, String>... params) {
    myParams = params;
  }

  @NotNull
  public abstract String getActionName();

  @Nullable
  public URL getUrl() {
    final String parameters = getParametersAsString();

    try {
      return new URL(SERVER_URL + "/" + getActionName() + (parameters.length() == 0 ? "" : "?" + parameters));
    }
    catch (MalformedURLException e) {
      LOG.error(e);
    }

    return null;
  }

  @NotNull
  private String getParametersAsString() {
    StringBuffer buffer = new StringBuffer();
    for (int i = 0; i < myParams.length; i++) {
      Pair<String, String> param = myParams[i];
      if (i > 0) buffer.append("&");
      buffer.append(param.first);
      buffer.append("=");
      buffer.append(param.second);
    }

    return buffer.toString();
  }
}
