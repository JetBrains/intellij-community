/*
 * @author Eugene Zhuravlev
 */
package com.intellij.compiler;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

public class CompilerWorkspaceConfiguration  implements JDOMExternalizable, ProjectComponent {

  public boolean COMPILE_IN_BACKGROUND = false;
  public boolean AUTO_SHOW_ERRORS_IN_EDITOR = true;
  public boolean CLOSE_MESSAGE_VIEW_IF_SUCCESS = true;
  public boolean COMPILE_DEPENDENT_FILES = false;
  public boolean CLEAR_OUTPUT_DIRECTORY = false;
  public boolean ASSERT_NOT_NULL = true;


  public static CompilerWorkspaceConfiguration getInstance(Project project) {
    return project.getComponent(CompilerWorkspaceConfiguration.class);
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public String getComponentName() {
    return "CompilerWorkspaceConfiguration";
  }

}
