// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.application.ApplicationManager;

public abstract class CommonReferenceProviderTypes {

  public static CommonReferenceProviderTypes getInstance() {
    return ApplicationManager.getApplication().getService(CommonReferenceProviderTypes.class);
  }

  public static final ReferenceProviderType PROPERTIES_FILE_KEY_PROVIDER = new ReferenceProviderType("Properties File Key Provider");

  public abstract PsiReferenceProvider getClassReferenceProvider();
}
