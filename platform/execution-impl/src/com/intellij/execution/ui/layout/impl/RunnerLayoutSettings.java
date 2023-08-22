// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.ui.layout.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.text.Strings;
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
  public static final String NOT_PERSISTENT_ID = "not_persistent_id";

  public static RunnerLayoutSettings getInstance() {
    return ApplicationManager.getApplication().getService(RunnerLayoutSettings.class);
  }

  private final Map<String, RunnerLayout> myRunnerId2Settings = new LinkedHashMap<>();

  public RunnerLayout getLayout(@NotNull String id) {
    if (Strings.areSameInstance(id, NOT_PERSISTENT_ID)) return new RunnerLayout();

    RunnerLayout layout = myRunnerId2Settings.get(id);
    if (layout == null) {
      layout = new RunnerLayout();
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
  public void loadState(@NotNull final Element state) {
    final List runners = state.getChildren("runner");
    for (Object each : runners) {
      Element eachRunnerElement = (Element)each;
      final String eachID = eachRunnerElement.getAttributeValue("id");
      final RunnerLayout eachLayout = new RunnerLayout();
      eachLayout.read(eachRunnerElement);
      myRunnerId2Settings.put(eachID, eachLayout);
    }
  }
}
