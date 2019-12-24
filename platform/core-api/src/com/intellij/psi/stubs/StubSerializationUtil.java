/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.stubs;

import org.jetbrains.annotations.NotNull;

/**
 * Author: dmitrylomov
 */
abstract class StubSerializationUtil {
  private StubSerializationUtil() {}

  static ObjectStubSerializer<Stub, Stub> getSerializer(@NotNull Stub rootStub) {
    if (rootStub instanceof PsiFileStub) {
      final PsiFileStub fileStub = (PsiFileStub)rootStub;
      return fileStub.getType();
    }

    return rootStub.getStubType();
  }

  /**
   * Format warning for {@link ObjectStubSerializer} not being able to deserialize given stub.
   *
   * @param root - serializer which couldn't deserialize stub
   * @return message for broken stub format
   */
  @NotNull
  static String brokenStubFormat(@NotNull final ObjectStubSerializer root) {
    return "Broken stub format, most likely version of " + root + " was not updated after serialization changes\n";
  }
}
