/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.builtInWebServer.ssi;

import io.netty.buffer.ByteBufUtf8Writer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface SsiCommand {
  long process(@NotNull SsiProcessingState state, @NotNull String commandName, @NotNull List<String> paramNames, @NotNull String[] paramValues, @NotNull ByteBufUtf8Writer writer);
}