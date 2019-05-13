/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.openapi.components.ServiceManager;

/**
 * @author peter
 */
public abstract class CommonReferenceProviderTypes {

  public static CommonReferenceProviderTypes getInstance() {
    return ServiceManager.getService(CommonReferenceProviderTypes.class);
  }

  public static final ReferenceProviderType PROPERTIES_FILE_KEY_PROVIDER = new ReferenceProviderType("Properties File Key Provider");
  public static final ReferenceProviderType URI_PROVIDER = new ReferenceProviderType("Uri references provider");
  public static final ReferenceProviderType SCHEMA_PROVIDER = new ReferenceProviderType("Schema references provider");

  public abstract PsiReferenceProvider getClassReferenceProvider();
}
