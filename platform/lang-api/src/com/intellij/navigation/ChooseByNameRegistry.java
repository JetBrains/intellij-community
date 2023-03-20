// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;

import java.util.List;

/**
 * Registry of components which contribute items to "Goto Class" and "Goto Symbol" lists.
 */
@Service
public final class ChooseByNameRegistry {

  /**
   * Returns the singleton instance of the registry.
   *
   * @return the registry instance.
   */
  public static ChooseByNameRegistry getInstance() {
    return ApplicationManager.getApplication().getService(ChooseByNameRegistry.class);
  }

  /**
   * Returns the list of registered contributors for the "Goto Class" list.
   *
   * @return the array of contributors.
   */
  public ChooseByNameContributor[] getClassModelContributors() {
    return ChooseByNameContributor.CLASS_EP_NAME.getExtensions();
  }

  /**
   * Returns the list of registered contributors for the "Goto Symbol" list.
   *
   * @return the list of contributors.
   */
  public List<ChooseByNameContributor> getSymbolModelContributors() {
    return ChooseByNameContributor.SYMBOL_EP_NAME.getExtensionList();
  }
}
