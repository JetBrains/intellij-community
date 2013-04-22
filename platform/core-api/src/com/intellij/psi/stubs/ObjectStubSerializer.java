/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author Dmitry Avdeev
 *         Date: 8/2/12
 */
public interface ObjectStubSerializer<T extends Stub, P extends Stub> {
  @NonNls
  @NotNull
  String getExternalId();

  void serialize(@NotNull T stub, @NotNull StubOutputStream dataStream) throws IOException;
  @NotNull
  T deserialize(@NotNull StubInputStream dataStream, final P parentStub) throws IOException;

  void indexStub(@NotNull T stub, @NotNull IndexSink sink);

}
