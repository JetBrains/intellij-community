/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.tree.render;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 9, 2005
 */
public abstract class NodeRendererImpl extends RendererImpl implements NodeRenderer{
  private String myName;

  protected NodeRendererImpl(RendererProvider rendererProvider, String uniqueId) {
    super(rendererProvider, uniqueId);
  }

  public final String getName() {
    return myName;
  }

  public final void setName(String text) {
    myName = text;
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myName = JDOMExternalizerUtil.readField(element, "NAME");
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, "NAME", myName);
  }
}
