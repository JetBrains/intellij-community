package com.intellij.compiler.impl.javaCompiler.eclipse;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

public class EclipseEmbeddedCompilerSettings extends EclipseCompilerSettings implements JDOMExternalizable, ProjectComponent {

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void projectClosed() {
  }

  public void projectOpened() {
  }

  public static EclipseEmbeddedCompilerSettings getInstance(Project project) {
    return project.getComponent(EclipseEmbeddedCompilerSettings.class);
  }

  public String getComponentName() {
    return "EclipseEmbeddedCompilerSettings";
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
