package com.intellij.openapi.vcs.versionBrowser;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.project.Project;
import org.jdom.Element;

public class ChangeBrowserSettings implements ProjectComponent, JDOMExternalizable{
  public float MAIN_SPLITTER_PROPORTION = 0.3f;
  public float MESSAGES_SPLITTER_PROPORTION = 0.8f;  

  public static final ChangeBrowserSettings getSettings(Project project){
    return project.getComponent(ChangeBrowserSettings.class);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public String getComponentName() {
    return "ChangeBrowserSettings";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
