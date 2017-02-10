/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.navigation;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/**
 * Registry of components which contribute items to "Goto Class" and "Goto Symbol" lists.
 */

public class ChooseByNameRegistry {
  private final List<ChooseByNameContributor> myGotoClassContributors = new ArrayList<>();
  private final List<ChooseByNameContributor> myGotoSymbolContributors = new ArrayList<>();
  private boolean myGotoClassExtensionsLoaded = false;
  private boolean myGotoSymbolExtensionsLoaded = false;

  /**
   * Returns the singleton instance of the registry.
   *
   * @return the registry instance.
   */
  public static ChooseByNameRegistry getInstance() {
    return ServiceManager.getService(ChooseByNameRegistry.class);
  }

  /**
   * Registers a component which contributes items to the "Goto Class" list.
   *
   * @param contributor the contributor instance.
   * @see #removeContributor(ChooseByNameContributor)
   * @deprecated use {@link com.intellij.navigation.ChooseByNameContributor#CLASS_EP_NAME} extension point instead
   */
  public void contributeToClasses(ChooseByNameContributor contributor) {
    myGotoClassContributors.add(contributor);
  }

  /**
   * Registers a component which contributes items to the "Goto Symbol" list.
   *
   * @param contributor the contributor instance.
   * @see #removeContributor(ChooseByNameContributor)
   * @deprecated use {@link com.intellij.navigation.ChooseByNameContributor#SYMBOL_EP_NAME} extension point instead
   */
  public void contributeToSymbols(ChooseByNameContributor contributor) {
    myGotoSymbolContributors.add(contributor);
  }

  /**
   * Unregisters a contributor for "Goto Class" and "Goto Symbol" lists.
   *
   * @param contributor the contributor instance.
   */
  public void removeContributor(ChooseByNameContributor contributor) {
    myGotoClassContributors.remove(contributor);
    myGotoSymbolContributors.remove(contributor);
  }

  /**
   * Returns the list of registered contributors for the "Goto Class" list.
   *
   * @return the array of contributors.
   */
  public ChooseByNameContributor[] getClassModelContributors() {
    if (!myGotoClassExtensionsLoaded) {
      myGotoClassExtensionsLoaded = true;
      Collections.addAll(myGotoClassContributors, Extensions.getExtensions(ChooseByNameContributor.CLASS_EP_NAME));
    }
    return myGotoClassContributors.toArray(new ChooseByNameContributor[myGotoClassContributors.size()]);
  }

  /**
   * Returns the list of registered contributors for the "Goto Symbol" list.
   *
   * @return the array of contributors.
   */
  public ChooseByNameContributor[] getSymbolModelContributors() {
    if (!myGotoSymbolExtensionsLoaded) {
      myGotoSymbolExtensionsLoaded = true;
      Collections.addAll(myGotoSymbolContributors, Extensions.getExtensions(ChooseByNameContributor.SYMBOL_EP_NAME));
    }
    return myGotoSymbolContributors.toArray(new ChooseByNameContributor[myGotoSymbolContributors.size()]);
  }

}
