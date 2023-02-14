// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.ext;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.util.xmlb.annotations.Attribute;

public final class LibraryDependentToolWindow extends ToolWindowEP {
  public static final ExtensionPointName<LibraryDependentToolWindow> EXTENSION_POINT_NAME =
    new ExtensionPointName<>("com.intellij.library.toolWindow");

  private LibrarySearchHelper myLibrarySearchHelper;

  @Attribute("librarySearchClass")
  public String librarySearchClass;

  /**
   * @deprecated not supported in new UI
   */
  @Deprecated
  @Attribute("showOnStripeByDefault")
  public boolean showOnStripeByDefault = true;

  public LibrarySearchHelper getLibrarySearchHelper() {
    if (myLibrarySearchHelper == null) {
      try {
        myLibrarySearchHelper = ApplicationManager.getApplication().instantiateClass(librarySearchClass, getPluginDescriptor());
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        Logger.getInstance(LibraryDependentToolWindow.class).error(e);
        return null;
      }
    }
    return myLibrarySearchHelper;
  }
}
