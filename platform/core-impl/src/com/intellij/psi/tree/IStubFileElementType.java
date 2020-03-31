// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.tree;

import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.stubs.*;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;

/*
 * @author max
 */
public class IStubFileElementType<T extends PsiFileStub> extends StubFileElementType<T> {
  private static volatile int templateStubVersion = -1;
  public IStubFileElementType(final Language language) {
    super(language);
  }

  public IStubFileElementType(@NonNls final String debugName, final Language language) {
    super(debugName, language);
    if (hasNonTrivialExternalId() && !isOutOfOurControl()) {
      IStubElementType.checkNotInstantiatedTooLate();
    }
  }

  private boolean hasNonTrivialExternalId() {
    return ReflectionUtil.getMethodDeclaringClass(getClass(), "getExternalId") != IStubFileElementType.class;
  }

  private boolean isOutOfOurControl() {
    return getClass().getName().contains(".kotlin."); // KT-28732
  }

  /**
   * Stub structure version. Should be incremented each time when stub tree changes (e.g. elements added/removed,
   * element serialization/deserialization changes).
   * Make sure to invoke super method for {@link TemplateLanguage} to prevent stub serialization problems due to
   * data language stub changes
   * @return stub version
   */
  public int getStubVersion() {
    return getLanguage() instanceof TemplateLanguage ? getTemplateStubVersion() : 0;
  }

  public StubBuilder getBuilder() {
    return new DefaultStubBuilder();
  }

  @NonNls
  @NotNull
  @Override
  public String getExternalId() {
    return DEFAULT_EXTERNAL_ID;
  }

  @Override
  public void serialize(@NotNull final T stub, @NotNull final StubOutputStream dataStream) throws IOException {
  }

  @NotNull
  @Override
  public T deserialize(@NotNull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    return (T)new PsiFileStubImpl(null);
  }

  @Override
  public void indexStub(@NotNull final PsiFileStub stub, @NotNull final IndexSink sink) {
  }

  public boolean shouldBuildStubFor(final VirtualFile file) {
    return true;
  }

  public static int getTemplateStubVersion() {
    if (templateStubVersion == -1) templateStubVersion = calcStubVersion();
    return templateStubVersion;
  }

  private static int calcStubVersion() {
    IElementType[] dataElementTypes = IElementType.enumerate(
      (elementType) -> elementType instanceof IStubFileElementType && !(elementType.getLanguage() instanceof TemplateLanguage));
    return Arrays.stream(dataElementTypes).mapToInt((e) -> ((IStubFileElementType)e).getStubVersion()).sum();
  }
}