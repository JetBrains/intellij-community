package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Type;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public abstract class ReferenceRenderer implements Renderer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.tree.render.ReferenceRenderer");
  protected BasicRendererProperties myProperties = new BasicRendererProperties();

  protected ReferenceRenderer() {
    this("java.lang.Object");
  }

  protected ReferenceRenderer(@NotNull String className) {
    myProperties.setClassName(className);
  }

  public String getClassName() {
    return myProperties.getClassName();
  }

  public void setClassName(String className) {
    myProperties.setClassName(className);
  }

  public Renderer clone() {
    try {
      final ReferenceRenderer cloned = (ReferenceRenderer)super.clone();
      cloned.myProperties = myProperties.clone();
      return cloned;
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
    }
    return null;
  }

  public boolean isApplicable(Type type) {
    return type != null && type instanceof ReferenceType && DebuggerUtils.instanceOf(type, getClassName());
  }

  public void writeExternal(Element element) throws WriteExternalException {
    myProperties.writeExternal(element);
  }

  public void readExternal(Element element) throws InvalidDataException {
    myProperties.readExternal(element);
  }
}
