// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class StubSerializationUtil {
  private StubSerializationUtil() {}

  public static ObjectStubSerializer<Stub, Stub> getSerializer(@NotNull Stub rootStub) {
    if (rootStub instanceof PsiFileStub) {
      ObjectStubSerializer serializer = StubElementRegistryService.getInstance().getStubSerializer(((PsiFileStub<?>)rootStub).getFileElementType());
      return (ObjectStubSerializer<Stub, Stub>)serializer;
    }
    //noinspection unchecked
    return (ObjectStubSerializer<Stub, Stub>)rootStub.getStubSerializer();
  }

  /**
   * Format warning for {@link ObjectStubSerializer} not being able to deserialize given stub.
   *
   * @param root - serializer which couldn't deserialize stub
   * @return message for broken stub format
   */
  static @NotNull @NonNls String brokenStubFormat(@NotNull ObjectStubSerializer<?, ?> root, @Nullable PsiFile file) {
    String fileInfo = file == null ? "" : " in file " + file.getName();
    return "Broken stub format" + fileInfo + ", most likely version of " + root + " (" + root.getExternalId() + ") was not updated after serialization changes\n";
  }
}
