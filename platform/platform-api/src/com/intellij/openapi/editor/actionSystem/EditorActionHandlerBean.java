package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.pico.IdeaPicoContainer;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author yole
 */
public class EditorActionHandlerBean extends AbstractExtensionPointBean {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.actionSystem.EditorActionHandlerBean");
  
  public static final ExtensionPointName<EditorActionHandlerBean> EP_NAME = ExtensionPointName.create("com.intellij.editorActionHandler");

  // these must be public for scrambling compatibility
  @Attribute("action")
  public String action;
  @Attribute("implementationClass")
  public String implementationClass;

  private EditorActionHandler myHandler;

  public EditorActionHandler getHandler(EditorActionHandler originalHandler) {
    if (myHandler == null) {
      try {
        IdeaPicoContainer container = new IdeaPicoContainer(ApplicationManager.getApplication().getPicoContainer());
        container.registerComponentInstance(originalHandler);
        myHandler = instantiate(implementationClass, container);
      }
      catch(Exception e) {
        LOG.error(e);
        return null;
      }
    }
    return myHandler;
  }
}
