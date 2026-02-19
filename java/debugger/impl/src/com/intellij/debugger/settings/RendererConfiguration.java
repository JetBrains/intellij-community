// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.settings;

import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.InternalIterator;
import org.jdom.Element;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RendererConfiguration implements Cloneable, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance(NodeRendererSettings.class);

  private static final int VERSION = 8;

  private List<NodeRenderer> myRepresentationNodes = new CopyOnWriteArrayList<>();
  private final NodeRendererSettings myRendererSettings;

  protected RendererConfiguration(NodeRendererSettings rendererSettings) {
    myRendererSettings = rendererSettings;
  }

  @Override
  public RendererConfiguration clone() {
    RendererConfiguration result = null;
    try {
      result = (RendererConfiguration)super.clone();
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
    }
    result.myRepresentationNodes = new CopyOnWriteArrayList<>();

    final ArrayList<NodeRenderer> cloned = new ArrayList<>();
    for (NodeRenderer renderer : myRepresentationNodes) {
      cloned.add((NodeRenderer)renderer.clone());
    }
    result.setRenderers(cloned);

    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof RendererConfiguration)) return false;

    return DebuggerUtilsEx.externalizableEqual(this, (RendererConfiguration)o);
  }

  @Override
  public void writeExternal(final Element element) throws WriteExternalException {
    for (NodeRenderer renderer : myRepresentationNodes) {
      element.addContent(myRendererSettings.writeRenderer(renderer));
    }
    element.setAttribute("VERSION", String.valueOf(VERSION));
  }

  @Override
  public void readExternal(final Element root) {
    int configurationVersion = StringUtil.parseInt(root.getAttributeValue("VERSION"), -1);
    if (configurationVersion != VERSION) {
      return;
    }

    final List<Element> children = root.getChildren(NodeRendererSettings.RENDERER_TAG);
    final List<NodeRenderer> renderers = new ArrayList<>(children.size());
    for (Element nodeElement : children) {
      try {
        renderers.add((NodeRenderer)myRendererSettings.readRenderer(nodeElement));
      }
      catch (Exception e) {
        LOG.debug(e);
      }
    }
    setRenderers(renderers);
  }

  public void addRenderer(NodeRenderer renderer) {
    myRepresentationNodes.add(0, renderer);
  }

  @TestOnly
  public void removeRenderer(NodeRenderer renderer) {
    myRepresentationNodes.remove(renderer);
  }

  public void setRenderers(Collection<? extends NodeRenderer> renderers) {
    myRepresentationNodes.clear();
    myRepresentationNodes.addAll(renderers);
  }

  public void iterateRenderers(InternalIterator<NodeRenderer> iterator) {
    for (final NodeRenderer renderer : myRepresentationNodes) {
      final boolean shouldContinue = iterator.visit(renderer);
      if (!shouldContinue) {
        break;
      }
    }
  }

  public int getRendererCount() {
    return myRepresentationNodes.size();
  }

  public boolean contains(NodeRenderer renderer) {
    return myRepresentationNodes.contains(renderer);
  }
}
