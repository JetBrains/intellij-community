// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.registry;

import com.intellij.openapi.application.ExperimentalFeature;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.SmartList;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@State(name = "Registry", storages = @Storage("ide.general.xml"))
final class RegistryState implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(RegistryState.class);

  @Override
  public Element getState() {
    return Registry.getInstance().getState();
  }

  @Override
  public void loadState(@NotNull Element state) {
    Registry.getInstance().loadState(state);

    Map<String, String> userProperties = Registry.getInstance().getUserProperties();
    if (userProperties.size() > (userProperties.containsKey("ide.firstStartup") ? 1 : 0)) {
      String[] keys = ArrayUtilRt.toStringArray(userProperties.keySet());
      Arrays.sort(keys);
      StringBuilder builder = new StringBuilder("Registry values changed by user: ");
      for (String key : keys) {
        if ("ide.firstStartup".equals(key)) {
          continue;
        }

        builder.append(key).append(" = ").append(userProperties.get(key)).append(", ");
      }
      LOG.info(builder.substring(0, builder.length() - 2));
    }

    // make logging for experimental features here to have registry + experiments together in the log file
    List<String> enabledIds = new SmartList<>();
    for (ExperimentalFeature e : Experiments.EP_NAME.getExtensionList()) {
      if (Experiments.getInstance().isFeatureEnabled(e.id)) {
        enabledIds.add(e.id);
      }
    }

    if (!enabledIds.isEmpty()) {
      LOG.info("Experimental features enabled for user: " + StringUtil.join(enabledIds, ", "));
    }
  }
}