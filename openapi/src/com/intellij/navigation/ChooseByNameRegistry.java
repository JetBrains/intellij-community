/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import java.util.ArrayList;
import java.util.List;

/**
 * Registry of components which contribute items to "Goto Class" and "Goto Symbol" lists.
 */

public class ChooseByNameRegistry {
  private List<ChooseByNameContributor> myGotoClassContributors = new ArrayList<ChooseByNameContributor>();
  private List<ChooseByNameContributor> myGotoSymbolContributors = new ArrayList<ChooseByNameContributor>();

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
   */
  public void contributeToClasses(ChooseByNameContributor contributor) {
    myGotoClassContributors.add(contributor);
  }

  /**
   * Registers a component which contributes items to the "Goto Symbol" list.
   *
   * @param contributor the contributor instance.
   * @see #removeContributor(ChooseByNameContributor)
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
    return myGotoClassContributors.toArray(new ChooseByNameContributor[myGotoClassContributors.size()]);
  }

  /**
   * Returns the list of registered contributors for the "Goto Symbol" list.
   *
   * @return the array of contributors.
   */
  public ChooseByNameContributor[] getSymbolModelContributors() {
    return myGotoSymbolContributors.toArray(new ChooseByNameContributor[myGotoSymbolContributors.size()]);
  }

}