// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.tree;

import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.stubs.*;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * OBSOLESCENCE NOTE:
 * Use {@link com.intellij.psi.stubs.LanguageStubDefinition}, {@link com.intellij.psi.stubs.StubElementFactory}, {@link com.intellij.psi.stubs.LightStubElementFactory} instead
 */
@ApiStatus.Obsolete
public class IStubFileElementType<T extends PsiFileStub> extends StubFileElementType<T> {
  public IStubFileElementType(Language language) {
    super(language);
  }

  public IStubFileElementType(@NonNls String debugName, Language language) {
    super(debugName, language);
    if (hasNonTrivialExternalId()) {
      IStubElementType.checkNotInstantiatedTooLate(getClass());
    }
    TemplateLanguageStubBaseVersion.dropVersion();
  }

  private boolean hasNonTrivialExternalId() {
    return ReflectionUtil.getMethodDeclaringClass(getClass(), "getExternalId") != IStubFileElementType.class;
  }

  /**
   * Stub structure version. Should be incremented each time when stub tree changes (e.g. elements added/removed,
   * element serialization/deserialization changes).
   * <p>
   * Make sure to invoke super method for {@link TemplateLanguage} to prevent stub serialization problems due to
   * data language stub changes.
   * <p>
   * Important: Negative values are not allowed! The platform relies on the fact that template languages have stub versions bigger than
   * {@link TemplateLanguageStubBaseVersion#getVersion()}, see {@link StubBuilderType#getVersion()}. At the same time
   * {@link TemplateLanguageStubBaseVersion#getVersion()} is computed as a sum of stub versions of all non-template languages.
   *
   * @return stub version
   */
  public int getStubVersion() {
    return getLanguage() instanceof TemplateLanguage ? TemplateLanguageStubBaseVersion.getVersion() : 0;
  }

  public StubBuilder getBuilder() {
    return new DefaultStubBuilder();
  }

  /**
   * Can only depend on getLanguage() or getDebugName(), should be calculated inplace.
   * MUST NOT be calculated from any other non-static local variables as it can lead to race-conditions
   * (<a href="https://youtrack.jetbrains.com/issue/IDEA-306646">IDEA-306646</a>).
   */
  @Override
  public @NonNls @NotNull String getExternalId() {
    return StubSerializerId.DEFAULT_EXTERNAL_ID;
  }

  @Override
  public void serialize(@NotNull T stub, @NotNull StubOutputStream dataStream) throws IOException {
  }

  @Override
  public @NotNull T deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return (T)new PsiFileStubImpl(null);
  }

  @Override
  public void indexStub(@NotNull PsiFileStub stub, @NotNull IndexSink sink) {
  }

  public boolean shouldBuildStubFor(VirtualFile file) {
    return true;
  }

  public static int getTemplateStubBaseVersion() {
    return TemplateLanguageStubBaseVersion.getVersion();
  }
}