package com.intellij.debugger.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

@State(
  name="ViewsSettings",
  storages= {
    @Storage(
      id="other",
      file = "$APP_CONFIG$/debugger.frameview.xml"
    )}
)
public class ViewsGeneralSettings implements PersistentStateComponent<Element> {
  public boolean SHOW_OBJECTID = true;
  public boolean HIDE_NULL_ARRAY_ELEMENTS = true;
  public boolean AUTOSCROLL_TO_NEW_LOCALS = true;
  private NodeRendererSettings myNodeRendererSettings;

  public ViewsGeneralSettings(NodeRendererSettings instance) {
    myNodeRendererSettings = instance;
  }

  public static ViewsGeneralSettings getInstance() {
    return ServiceManager.getService(ViewsGeneralSettings.class);
  }

  public void loadState(Element element) {
    try {
      DefaultJDOMExternalizer.readExternal(this, element);
    }
    catch (InvalidDataException e) {
      // ignore
    }
  }

  public Element getState() {
    Element element = new Element("ViewsGeneralSettings");
    try {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }
    catch (WriteExternalException e) {
      // ignore
    }
    return element;
  }

  void fireRendererSettingsChanged() {
    myNodeRendererSettings.fireRenderersChanged();
  }

  public boolean equals(Object object) {
    if(!(object instanceof ViewsGeneralSettings)) return false;
    ViewsGeneralSettings generalSettings = ((ViewsGeneralSettings) object);
    return SHOW_OBJECTID == generalSettings.SHOW_OBJECTID &&
           HIDE_NULL_ARRAY_ELEMENTS == generalSettings.HIDE_NULL_ARRAY_ELEMENTS &&
           AUTOSCROLL_TO_NEW_LOCALS == generalSettings.AUTOSCROLL_TO_NEW_LOCALS;
  }

}