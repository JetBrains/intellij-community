/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */

package com.intellij.util.xml;

import com.intellij.openapi.project.Project;

import java.util.Set;

/**
 * User: Sergey.Vasiliev
 */
public abstract class DomElementsNavigationManager {
  public static String DEFAULT_PROVIDER_NAME = "DEFAULT_PROVIDER_NAME";

  public static DomElementsNavigationManager getManager(Project project) {
    return project.getComponent(DomElementsNavigationManager.class);
  }

  public abstract Set<DomElementNavigationProvider> getDomElementsNavigateProviders(DomElement domElement);

  public abstract DomElementNavigationProvider getDomElementsNavigateProvider(String providerName);

  public abstract void registerDomElementsNavigateProvider(DomElementNavigationProvider provider);


}
