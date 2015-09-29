/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.CommonClassNames;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Type;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public abstract class ReferenceRenderer implements Renderer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.tree.render.ReferenceRenderer");
  protected BasicRendererProperties myProperties = new BasicRendererProperties();

  protected ReferenceRenderer() {
    this(CommonClassNames.JAVA_LANG_OBJECT);
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

  protected CachedEvaluator createCachedEvaluator() {
    return new CachedEvaluator() {
      protected String getClassName() {
        return ReferenceRenderer.this.getClassName();
      }
    };
  }
}
