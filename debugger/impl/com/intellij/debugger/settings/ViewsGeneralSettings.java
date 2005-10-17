package com.intellij.debugger.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

public class ViewsGeneralSettings implements NamedJDOMExternalizable, ApplicationComponent{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.settings.ViewsSettings");
  public boolean SHOW_OBJECTID = true;
  public boolean HIDE_NULL_ARRAY_ELEMENTS = true;
  public boolean AUTOSCROLL_TO_NEW_LOCALS = true;
  private NodeRendererSettings myNodeRendererSettings;

  public ViewsGeneralSettings(NodeRendererSettings instance) {
    myNodeRendererSettings = instance;
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public static ViewsGeneralSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(ViewsGeneralSettings.class);
  }

  public String getExternalFileName() {
    return "debugger.frameview";
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public String getComponentName() {
    return "ViewsSettings";
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