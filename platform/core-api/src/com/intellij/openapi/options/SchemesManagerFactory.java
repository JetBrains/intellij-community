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
package com.intellij.openapi.options;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.ServiceBean;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.ExtensionPointName;

public abstract class SchemesManagerFactory {
  public static ExtensionPointName<ServiceBean> SCHEME_OWNER = ExtensionPointName.create("com.intellij.schemeOwner");  

  public abstract <T extends Scheme, E extends ExternalizableScheme> SchemesManager<T,E> createSchemesManager(String fileSpec, SchemeProcessor<E> processor,
                                                                            RoamingType roamingType);

  public static SchemesManagerFactory getInstance(){
    return ServiceManager.getService(SchemesManagerFactory.class);
  }


  public abstract void updateConfigFilesFromStreamProviders();
}
