// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import org.jetbrains.annotations.NotNull;

final class StubSerializationUtil {
  private StubSerializationUtil() {}

  static ObjectStubSerializer<Stub, Stub> getSerializer(@NotNull Stub rootStub) {
    if (rootStub instanceof PsiFileStub) {
      //noinspection unchecked
      return ((PsiFileStub<?>)rootStub).getType();
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
  static @NotNull String brokenStubFormat(@NotNull ObjectStubSerializer<?, ?> root) {
    return "Broken stub format, most likely version of " + root + " was not updated after serialization changes\n";
  }
}
