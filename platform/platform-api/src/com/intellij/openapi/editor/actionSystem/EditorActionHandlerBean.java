// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actionSystem;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.pico.DefaultPicoContainer;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registers action invoked in the editor.
 *
 * @author yole
 */
public final class EditorActionHandlerBean extends AbstractExtensionPointBean {

  private static final Logger LOG = Logger.getInstance(EditorActionHandlerBean.class);

  @ApiStatus.Internal
  public static final ExtensionPointName<EditorActionHandlerBean> EP_NAME = ExtensionPointName.create("com.intellij.editorActionHandler");

  /**
   * Action ID.
   */
  @Attribute("action")
  @RequiredElement
  public String action;

  @Attribute("implementationClass")
  @RequiredElement
  public String implementationClass;

  private EditorActionHandler myHandler;

  public EditorActionHandler getHandler(EditorActionHandler originalHandler) {
    if (myHandler == null) {
      try {
        DefaultPicoContainer container = new DefaultPicoContainer(ApplicationManager.getApplication().getPicoContainer());
        container.registerComponentInstance(originalHandler);
        myHandler = instantiateClass(implementationClass, container);
      }
      catch (Exception e) {
        LOG.error(new PluginException(e, getPluginId()));
        return null;
      }
    }
    return myHandler;
  }
}
