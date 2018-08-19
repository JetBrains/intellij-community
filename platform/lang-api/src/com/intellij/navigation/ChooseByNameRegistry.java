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
import com.intellij.util.containers.ContainerUtil;

import java.util.Arrays;
import java.util.List;

/**
 * Registry of components which contribute items to "Goto Class" and "Goto Symbol" lists.
 */

public class ChooseByNameRegistry {
  private final List<ChooseByNameContributor> myGotoSymbolContributors = ContainerUtil.createLockFreeCopyOnWriteList();

  /**
   * Returns the singleton instance of the registry.
   *
   * @return the registry instance.
   */
  public static ChooseByNameRegistry getInstance() {
    return ServiceManager.getService(ChooseByNameRegistry.class);
  }

  /**
   * Registers a component which contributes items to the "Goto Symbol" list.
   *
   * @param contributor the contributor instance.
   * @deprecated use {@link com.intellij.navigation.ChooseByNameContributor#SYMBOL_EP_NAME} extension point instead
   */
  @Deprecated
  public void contributeToSymbols(ChooseByNameContributor contributor) {
    myGotoSymbolContributors.add(contributor);
  }

  /**
   * Returns the list of registered contributors for the "Goto Class" list.
   *
   * @return the array of contributors.
   */
  public ChooseByNameContributor[] getClassModelContributors() {
    return Extensions.getExtensions(ChooseByNameContributor.CLASS_EP_NAME);
  }

  /**
   * Returns the list of registered contributors for the "Goto Symbol" list.
   *
   * @return the array of contributors.
   */
  public ChooseByNameContributor[] getSymbolModelContributors() {
    ChooseByNameContributor[] extensions = Extensions.getExtensions(ChooseByNameContributor.SYMBOL_EP_NAME);
    if (myGotoSymbolContributors.isEmpty()) {
      return extensions;
    }

    List<ChooseByNameContributor> concat = ContainerUtil.concat(myGotoSymbolContributors, Arrays.asList(extensions));
    return concat.toArray(new ChooseByNameContributor[0]);
  }

}
