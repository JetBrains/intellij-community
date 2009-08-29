/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

/**
 * @author peter
 */
public abstract class CommonReferenceProviderTypes {

  public static CommonReferenceProviderTypes getInstance(final Project project) {
    return ServiceManager.getService(project, CommonReferenceProviderTypes.class);
  }


  public static final ReferenceProviderType PROPERTIES_FILE_KEY_PROVIDER = new ReferenceProviderType("Properties File Key Provider");
  public static final ReferenceProviderType URI_PROVIDER = new ReferenceProviderType("Uri references provider");
  public static final ReferenceProviderType SCHEMA_PROVIDER = new ReferenceProviderType("Schema references provider");

  public abstract PsiReferenceProvider getClassReferenceProvider();
}
