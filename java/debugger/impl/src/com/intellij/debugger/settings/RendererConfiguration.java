/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.settings;

import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.InternalIterator;
import org.jdom.Element;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RendererConfiguration implements Cloneable, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.settings.NodeRendererSettings");

  private static final int VERSION = 8;

  private List<NodeRenderer> myRepresentationNodes = new CopyOnWriteArrayList<>();
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
    result.myRepresentationNodes = new CopyOnWriteArrayList<>();

    final ArrayList<NodeRenderer> cloned = new ArrayList<>();
    for (NodeRenderer renderer : myRepresentationNodes) {
      cloned.add((NodeRenderer)renderer.clone());
    }
    result.setRenderers(cloned);

    return result;
  }

  public boolean equals(Object o) {
    if(!(o instanceof RendererConfiguration)) return false;

    return DebuggerUtilsEx.externalizableEqual(this, (RendererConfiguration)o);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void writeExternal(final Element element) throws WriteExternalException {
    for (NodeRenderer renderer : myRepresentationNodes) {
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

  public void setRenderers(Collection<NodeRenderer> renderers) {
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
}
