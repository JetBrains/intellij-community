// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.CommonClassNames;
import com.sun.jdi.Type;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public abstract class ReferenceRenderer implements Renderer {
  private static final Logger LOG = Logger.getInstance(ReferenceRenderer.class);
  protected BasicRendererProperties myProperties = new BasicRendererProperties(false);

  protected ReferenceRenderer() {
    this(CommonClassNames.JAVA_LANG_OBJECT);
  }

  protected ReferenceRenderer(@NotNull String className) {
    myProperties.setClassName(className);
  }

  @Override
  public CompletableFuture<Boolean> isApplicableAsync(Type type) {
    return DebuggerUtilsAsync.instanceOf(type, getClassName());
  }

  public String getClassName() {
    return myProperties.getClassName();
  }

  public void setClassName(String className) {
    myProperties.setClassName(className);
  }

  @Override
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

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    myProperties.writeExternal(element, CommonClassNames.JAVA_LANG_OBJECT);
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    myProperties.readExternal(element, CommonClassNames.JAVA_LANG_OBJECT);
  }

  protected CachedEvaluator createCachedEvaluator() {
    return new CachedEvaluator() {
      @Override
      protected String getClassName() {
        return ReferenceRenderer.this.getClassName();
      }
    };
  }
}
