// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * Registry of components which contribute items to "Goto Class" and "Goto Symbol" lists.
 */
@Service
public final class ChooseByNameRegistry {
  private final List<ChooseByNameContributor> myGotoSymbolContributors = ContainerUtil.createLockFreeCopyOnWriteList();

  /**
   * Returns the singleton instance of the registry.
   *
   * @return the registry instance.
   */
  public static ChooseByNameRegistry getInstance() {
    return ApplicationManager.getApplication().getService(ChooseByNameRegistry.class);
  }

  /**
   * Registers a component which contributes items to the "Goto Symbol" list.
   *
   * @param contributor the contributor instance.
   * @deprecated use {@link ChooseByNameContributor#SYMBOL_EP_NAME} extension point instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public void contributeToSymbols(ChooseByNameContributor contributor) {
    myGotoSymbolContributors.add(contributor);
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
   * @return the array of contributors.
   */
  public List<ChooseByNameContributor> getSymbolModelContributors() {
    List<ChooseByNameContributor> extensions = ChooseByNameContributor.SYMBOL_EP_NAME.getExtensionList();
    if (myGotoSymbolContributors.isEmpty()) {
      return extensions;
    }

    return ContainerUtil.concat(myGotoSymbolContributors, extensions);
  }
}
