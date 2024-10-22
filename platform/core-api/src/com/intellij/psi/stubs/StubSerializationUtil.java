// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class StubSerializationUtil {
  private StubSerializationUtil() {}

  static ObjectStubSerializer<Stub, Stub> getSerializer(@NotNull Stub rootStub) {
    if (rootStub instanceof PsiFileStub) {
      //noinspection unchecked
      return ((PsiFileStub)rootStub).getType();
    }
    //noinspection unchecked
    return (ObjectStubSerializer<Stub, Stub>)rootStub.getStubType();
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
