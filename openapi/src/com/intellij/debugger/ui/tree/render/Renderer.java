package com.intellij.debugger.ui.tree.render;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizable;
import com.sun.jdi.Type;

public interface Renderer extends Cloneable, JDOMExternalizable {
  RendererProvider getRendererProvider();

  String getUniqueId();

  /***
   * Checks whether this renderer is apllicable to this value
   * @param type
   */
  public boolean isApplicable(Type type);

  public Object clone() throws CloneNotSupportedException;
}
