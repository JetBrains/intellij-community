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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;

import java.util.ArrayList;
import java.util.List;

public class ChooseByNameRegistry implements ApplicationComponent {
  private List<ChooseByNameContributor> myGotoClassContributors = new ArrayList<ChooseByNameContributor>();
  private List<ChooseByNameContributor> myGotoSymbolContributors = new ArrayList<ChooseByNameContributor>();

  public static ChooseByNameRegistry getInstance() {
    return ApplicationManager.getApplication().getComponent(ChooseByNameRegistry.class);
  }

  public void contributeToClasses(ChooseByNameContributor contributor) {
    myGotoClassContributors.add(contributor);
  }

  public void contributeToSymbols(ChooseByNameContributor contributor) {
    myGotoSymbolContributors.add(contributor);
  }

  public void removeContributor(ChooseByNameContributor contributor) {
    myGotoClassContributors.remove(contributor);
    myGotoSymbolContributors.remove(contributor);
  }

  public ChooseByNameContributor[] getClassModelContributors() {
    return myGotoClassContributors.toArray(new ChooseByNameContributor[myGotoClassContributors.size()]);
  }

  public ChooseByNameContributor[] getSymbolModelContributors() {
    return myGotoSymbolContributors.toArray(new ChooseByNameContributor[myGotoSymbolContributors.size()]);
  }

  public String getComponentName() {
    return "ChooseByNameRegistry";
  }

  public void initComponent() {}
  public void disposeComponent() {}
}