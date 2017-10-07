// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.openapi.util.registry;

import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;

import java.util.*;
import java.util.stream.Collectors;

@State(
  name = "Registry",
  storages = {
    @Storage("ide.general.xml"),
    @Storage(value = "other.xml", deprecated = true)
  }
)
public class RegistryState implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(RegistryState.class);

  @Override
  public Element getState() {
    return Registry.getInstance().getState();
  }

  @Override
  public void loadState(Element state) {
    Registry.getInstance().loadState(state);
    SortedMap<String, String> userProperties = new TreeMap<>(Registry.getInstance().getUserProperties());
    userProperties.remove("ide.firstStartup");
    if (!userProperties.isEmpty()) {
      LOG.info("Registry values changed by user:");
      for (Map.Entry<String, String> entry : userProperties.entrySet()) {
        LOG.info("  " + entry.getKey() + " = " + entry.getValue());
      }
    }

    //make logging for experimental features here to have registry + experiments together in the log file
    List<String> enabledIds = Arrays.stream(Experiments.EP_NAME.getExtensions())
      .filter(e -> Experiments.isFeatureEnabled(e.id))
      .map(e -> e.id)
      .collect(Collectors.toList());

    if (!enabledIds.isEmpty()) {
      LOG.info("Experimental features enabled for user: " + StringUtil.join(enabledIds, ", "));
    }
  }
}