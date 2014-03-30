/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 9, 2005
 */
public abstract class NodeRendererImpl implements NodeRenderer{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.tree.render.NodeRendererImpl");
  protected BasicRendererProperties myProperties = new BasicRendererProperties();

  protected NodeRendererImpl() {
    this("unnamed");
  }

  protected NodeRendererImpl(@NotNull String presentableName) {
    myProperties.setName(presentableName);
  }

  public String getName() {
    return myProperties.getName();
  }

  public void setName(String name) {
    myProperties.setName(name);
  }

  public boolean isEnabled() {
    return myProperties.isEnabled();
  }

  public void setEnabled(boolean enabled) {
    myProperties.setEnabled(enabled);
  }

  public Icon calcValueIcon(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener) throws EvaluateException {
    return null;
  }

  public NodeRendererImpl clone() {
    try {
      final NodeRendererImpl cloned = (NodeRendererImpl)super.clone();
      cloned.myProperties = myProperties.clone();
      return cloned;
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
    }
    return null;
  }

  public void readExternal(Element element) throws InvalidDataException {
    myProperties.readExternal(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    myProperties.writeExternal(element);
  }

  public String toString() {
    return getName();
  }

  @Nullable
  public String getIdLabel(Value value, DebugProcess process) {
    return value instanceof ObjectReference ? ValueDescriptorImpl.getIdLabel((ObjectReference)value) : null;
  }
}
