package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Type;
import org.jdom.Element;

public abstract class ReferenceRenderer extends RendererImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.tree.render.ReferenceRenderer");
  private String myClassName; // todo: remove from here, use support from RendererImpl

  protected ReferenceRenderer(final RendererProvider rendererProvider, final String uniqueId) {
    this("java.lang.Object", rendererProvider, uniqueId);
  }

  protected ReferenceRenderer(String className, final RendererProvider rendererProvider, final String uniqueId) {
    super(rendererProvider, uniqueId);
    LOG.assertTrue(className != null);
    myClassName = className;
  }

  public String getClassName() {
    return myClassName;
  }

  public void setClassName(String className) {
    myClassName = className;
  }

  public boolean isApplicable(Type type) {
    if(type == null || !(type instanceof ReferenceType)) {
      return false;
    }

    return DebuggerUtils.instanceOf(type, myClassName);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, "QUALIFIED_NAME", getClassName());
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    final String className = JDOMExternalizerUtil.readField(element, "QUALIFIED_NAME");
    if(className != null) {
      setClassName(className);
    }
  }
}
