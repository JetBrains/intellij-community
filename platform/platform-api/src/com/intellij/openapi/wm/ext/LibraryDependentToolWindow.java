package com.intellij.openapi.wm.ext;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.util.xmlb.annotations.Attribute;

public class LibraryDependentToolWindow extends ToolWindowEP {

  public static final ExtensionPointName<LibraryDependentToolWindow> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.library.toolWindow");

  private static final Logger LOG = Logger.getInstance("com.intellij.javaee.toolwindow.LibraryDependentToolWindow");

  private LibrarySearchHelper myLibrarySearchHelper;

  @Attribute("librarySearchClass")
  public String librarySearchClass;

  public LibrarySearchHelper getLibrarySearchHelper() {
    if (myLibrarySearchHelper == null) {
      try {
        myLibrarySearchHelper = instantiate(getLibrarySearchClass(), ApplicationManager.getApplication().getPicoContainer());
      }
      catch(Exception e) {
        LOG.error(e);
        return null;
      }
    }
    return myLibrarySearchHelper;
  }

  public String getLibrarySearchClass() {
    return librarySearchClass;
  }
}
