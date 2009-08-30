package com.intellij.debugger.settings;

import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.InternalIterator;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RendererConfiguration implements Cloneable, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.settings.NodeRendererSettings");

  private static final int VERSION = 8;

  private List<NodeRenderer> myRepresentationNodes = new ArrayList<NodeRenderer>();
  private final NodeRendererSettings myRendererSettings;

  protected RendererConfiguration(NodeRendererSettings rendererSettings) {
    myRendererSettings = rendererSettings;
  }

  public RendererConfiguration clone() {
    RendererConfiguration result = null;
    try {
      result = (RendererConfiguration)super.clone();
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
    }
    result.myRepresentationNodes = new ArrayList<NodeRenderer>();
    for (Iterator<NodeRenderer> iterator = myRepresentationNodes.iterator(); iterator.hasNext();) {
      result.addRenderer((NodeRenderer)iterator.next().clone());
    }

    return result;
  }

  public boolean equals(Object o) {
    if(!(o instanceof RendererConfiguration)) return false;

    return DebuggerUtilsEx.externalizableEqual(this, (RendererConfiguration)o);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void writeExternal(final Element element) throws WriteExternalException {
    for (Iterator<NodeRenderer> iterator = myRepresentationNodes.iterator(); iterator.hasNext();) {
      NodeRenderer renderer = iterator.next();
      element.addContent(myRendererSettings.writeRenderer(renderer));
    }
    element.setAttribute("VERSION", String.valueOf(VERSION));
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void readExternal(final Element root) {
    String versionAttrib = root.getAttributeValue("VERSION");
    int configurationVersion = -1;
    if (versionAttrib != null) {
      try {
        configurationVersion = Integer.parseInt(versionAttrib);
      }
      catch (NumberFormatException e) {
        configurationVersion = -1;
      }
    }
    if(configurationVersion != VERSION) {
      return;
    }

    List<Element> children = root.getChildren(NodeRendererSettings.RENDERER_TAG);

    myRepresentationNodes.clear();
    for (Element nodeElement : children) {
      try {
        addRenderer((NodeRenderer)myRendererSettings.readRenderer(nodeElement));
      }
      catch (Exception e) {
        LOG.debug(e);
      }
    }
  }

  public void addRenderer(NodeRenderer renderer) {
    myRepresentationNodes.add(renderer);
  }

  public void removeRenderer(NodeRenderer renderer) {
    myRepresentationNodes.remove(renderer);
  }

  public void removeAllRenderers() {
    myRepresentationNodes.clear();
  }

  public void iterateRenderers(InternalIterator<NodeRenderer> iterator) {
    for (Iterator<NodeRenderer> it = myRepresentationNodes.iterator(); it.hasNext();) {
      final NodeRenderer renderer = it.next();
      final boolean shouldContinue = iterator.visit(renderer);
      if (!shouldContinue) {
        break;
      }
    }
  }

  public int getRendererCount() {
    return myRepresentationNodes.size();
  }
}
