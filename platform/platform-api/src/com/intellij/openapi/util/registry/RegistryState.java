package com.intellij.openapi.util.registry;

import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

@State(
    name = "Registry",
    storages = {
        @Storage(
            id = "other",
            file="$APP_CONFIG$/other.xml")}
)
public class RegistryState implements BaseComponent, PersistentStateComponent<Element> {

  public RegistryState() {
  }

  public Element getState() {
    return Registry.getInstance().getState();
  }

  public void loadState(Element state) {
    Registry.getInstance().loadState(state);
  }

  @NotNull
  public String getComponentName() {
    return "Registry";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}