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

package com.intellij.execution.ui.layout.impl;

import com.intellij.openapi.components.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@State(
  name = "RunnerLayoutSettings",
  storages = @Storage(value = "runner.layout.xml", roamingType = RoamingType.DISABLED)
)
public class RunnerLayoutSettings implements PersistentStateComponent<Element> {
  public static RunnerLayoutSettings getInstance() {
    return ServiceManager.getService(RunnerLayoutSettings.class);
  }

  private final Map<String, RunnerLayout> myRunnerId2Settings = new LinkedHashMap<>();

  public RunnerLayout getLayout(@NotNull String id) {
    RunnerLayout layout = myRunnerId2Settings.get(id);
    if (layout == null) {
      layout = new RunnerLayout(id);
      myRunnerId2Settings.put(id, layout);
    }

    return layout;
  }

  @Override
  public Element getState() {
    final Element runners = new Element("runners");
    for (String eachID : myRunnerId2Settings.keySet()) {
      final RunnerLayout layout = myRunnerId2Settings.get(eachID);
      final Element runnerElement = new Element("runner");
      runnerElement.setAttribute("id", eachID);
      layout.write(runnerElement);
      runners.addContent(runnerElement);
    }
    return runners;
  }

  @Override
  public void loadState(final Element state) {
    final List runners = state.getChildren("runner");
    for (Object each : runners) {
      Element eachRunnerElement = (Element)each;
      final String eachID = eachRunnerElement.getAttributeValue("id");
      final RunnerLayout eachLayout = new RunnerLayout(eachID);
      eachLayout.read(eachRunnerElement);
      myRunnerId2Settings.put(eachID, eachLayout);
    }
  }
}
