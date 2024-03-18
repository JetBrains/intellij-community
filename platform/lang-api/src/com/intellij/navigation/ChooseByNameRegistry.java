// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.navigation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import org.jetbrains.annotations.NotNull;

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
   * @deprecated Use {@link #getClassModelContributorList()}
   */
  @Deprecated
  public ChooseByNameContributor[] getClassModelContributors() {
    return ChooseByNameContributor.CLASS_EP_NAME.getExtensions();
  }

  /**
   * Returns the list of registered contributors for the "Goto Class" list.
   */
  public @NotNull List<ChooseByNameContributor> getClassModelContributorList() {
    return ChooseByNameContributor.CLASS_EP_NAME.getExtensionList();
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
