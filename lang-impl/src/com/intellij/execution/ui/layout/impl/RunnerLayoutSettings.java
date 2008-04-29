package com.intellij.execution.ui.layout.impl;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@State(
  name = "RunnerLayoutSettings",
  storages = {@Storage(
    id = "other",
    file = "$APP_CONFIG$/runner.layout.xml")})
public class RunnerLayoutSettings implements PersistentStateComponent<Element>, ApplicationComponent {

  private Map<String, RunnerLayoutImpl> myRunnerId2Settings = new LinkedHashMap<String, RunnerLayoutImpl>();

  public RunnerLayoutImpl getLayout(String id) {
    RunnerLayoutImpl layout = myRunnerId2Settings.get(id);
    if (layout == null) {
      layout = new RunnerLayoutImpl(id);
      myRunnerId2Settings.put(id, layout);
    }

    return layout;
  }

  public Element getState() {
    final Element runners = new Element("runners");
    for (String eachID : myRunnerId2Settings.keySet()) {
      final RunnerLayoutImpl layout = myRunnerId2Settings.get(eachID);
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
      final RunnerLayoutImpl eachLayout = new RunnerLayoutImpl(eachID);
      eachLayout.read(eachRunnerElement);
      myRunnerId2Settings.put(eachID, eachLayout);
    }
  }

  @NotNull
  public String getComponentName() {
    return "RunnerLayoutSettings";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
