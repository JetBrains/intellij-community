package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Type;
import org.jdom.Element;

/**
 * User: lex
 * Date: Oct 10, 2003
 * Time: 9:31:06 PM
 */
public abstract class ReferenceRenderer implements Renderer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.tree.render.ReferenceRenderer");
  private String myClassName;

  protected ReferenceRenderer() {
    this("java.lang.Object");
  }

  protected ReferenceRenderer(String className) {
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
    if(type == null || !(type instanceof ReferenceType)) return false;

    return DebuggerUtils.instanceOf(((ReferenceType)type), getClassName());
  }

  public Renderer clone() {
    try {
      return (Renderer)super.clone();
    }
    catch (CloneNotSupportedException e) {
      LOG.assertTrue(false);
      return null;
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    JDOMExternalizerUtil.writeField(element, "QUALIFIED_NAME", getClassName());
  }

  public void readExternal(Element element) throws InvalidDataException {
    String className = JDOMExternalizerUtil.readField(element, "QUALIFIED_NAME");
    if(className != null) {
      setClassName(className);
    }
  }
}
