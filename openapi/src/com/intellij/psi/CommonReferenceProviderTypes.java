/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

/**
 * @author peter
 */
public class CommonReferenceProviderTypes {
  public static final ReferenceProviderType PROPERTIES_FILE_KEY_PROVIDER = new ReferenceProviderType("Properties File Key Provider");
  public static final ReferenceProviderType CLASS_REFERENCE_PROVIDER = new ReferenceProviderType("Class Reference Provider");
  public static final ReferenceProviderType URI_PROVIDER = new ReferenceProviderType("Uri references provider");
  public static final ReferenceProviderType SCHEMA_PROVIDER = new ReferenceProviderType("Schema references provider");
}
