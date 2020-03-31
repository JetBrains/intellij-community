// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.pico.DefaultPicoContainer;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author yole
 */
public class EditorTypedHandlerBean extends AbstractExtensionPointBean {
  private static final Logger LOG = Logger.getInstance(EditorActionHandlerBean.class);

  public static final ExtensionPointName<EditorTypedHandlerBean> EP_NAME = ExtensionPointName.create("com.intellij.editorTypedHandler");
  public static final ExtensionPointName<EditorTypedHandlerBean> RAW_EP_NAME = ExtensionPointName.create("com.intellij.rawEditorTypedHandler");

  // these must be public for scrambling compatibility
  @Attribute("implementationClass")
  public String implementationClass;

  private TypedActionHandler myHandler;

  public TypedActionHandler getHandler(TypedActionHandler originalHandler) {
    if (myHandler == null) {
      try {
        DefaultPicoContainer container = new DefaultPicoContainer(ApplicationManager.getApplication().getPicoContainer());
        container.registerComponentInstance(originalHandler);
        myHandler = instantiateClass(implementationClass, container);
      }
      catch(Exception e) {
        LOG.error(e);
        return null;
      }
    }
    return myHandler;
  }
}