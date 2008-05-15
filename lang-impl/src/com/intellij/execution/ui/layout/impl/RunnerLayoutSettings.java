package com.intellij.execution.ui.layout.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jdom.Element;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@State(
  name = "RunnerLayoutSettings",
  storages = {@Storage(
    id = "other",
    file = "$APP_CONFIG$/runner.layout.xml")})
public class RunnerLayoutSettings implements PersistentStateComponent<Element> {
  public static RunnerLayoutSettings getInstance() {
    return ServiceManager.getService(RunnerLayoutSettings.class);
  }

  private Map<String, RunnerLayout> myRunnerId2Settings = new LinkedHashMap<String, RunnerLayout>();

  public RunnerLayout getLayout(String id) {
    RunnerLayout layout = myRunnerId2Settings.get(id);
    if (layout == null) {
      layout = new RunnerLayout(id);
      myRunnerId2Settings.put(id, layout);
    }

    return layout;
  }

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
