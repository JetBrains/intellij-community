/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.fileTypes.FileTypeExtension;

/**
 * @author peter
 */
public class LanguageSubstitutors extends FileTypeExtension<LanguageSubstitutor> {
  public static LanguageSubstitutors INSTANCE = new LanguageSubstitutors();

  private LanguageSubstitutors() {
    super("com.intellij.fileType.languageSubstitutor");
  }
}
