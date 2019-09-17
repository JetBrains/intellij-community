// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public interface PsiClassStub<T extends PsiClass> extends PsiMemberStub<T> {
  @Nullable String getQualifiedName();

  @Nullable String getBaseClassReferenceText();

  boolean isInterface();

  boolean isEnum();

  boolean isEnumConstantInitializer();

  boolean isAnonymous();

  boolean isAnonymousInQualifiedNew();

  boolean isAnnotationType();

  @Nullable String getSourceFileName();

  /** @deprecated use {@link PsiJavaFileStub#getLanguageLevel()} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  LanguageLevel getLanguageLevel();
}