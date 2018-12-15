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
package org.jetbrains.jps.incremental.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.CompileContext;

/**
 * An extension for processing custom messages that had been sent by {@link org.jetbrains.jps.javac.DiagnosticOutputConsumer#customOutputData}
 */
public interface CustomOutputDataListener {

  @NotNull
  String getId();

  /**
   * Custom data passed through DiagnosticOutputConsumer object with pluginId == thisExtension.getId() will be passed to this method
   * @param context
   * @param dataName
   * @param content
   */
  void processData(CompileContext context, @Nullable String dataName, @NotNull byte[] content);
}
