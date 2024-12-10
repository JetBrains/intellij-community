// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.tree;

import com.intellij.lang.Language;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubSerializer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for file element types having stubs.
 *
 * @author Konstantin.Ulitin
 *
 * OBSOLESCENCE NOTE:
 * Use {@link com.intellij.psi.stubs.LanguageStubDefinition}, {@link com.intellij.psi.stubs.StubElementFactory}, {@link com.intellij.psi.stubs.LightStubElementFactory} instead
 */
@ApiStatus.Obsolete
public abstract class StubFileElementType<T extends PsiFileStub> extends IFileElementType implements StubSerializer<T> {

  /** @deprecated this constant is unused */
  @Deprecated
  public static final String DEFAULT_EXTERNAL_ID = "psi.file";

  public StubFileElementType(@Nullable Language language) {
    super(language);
  }

  public StubFileElementType(@NonNls @NotNull String debugName, @Nullable Language language) {
    super(debugName, language);
  }
}
