/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

/**
 * A generic extension to enable plugging into various searches.<p/>
 * 
 * Consider extending {@link com.intellij.openapi.application.QueryExecutorBase} instead unless you know what you're doing.
 *
 * @author max
 */
public interface QueryExecutor<Result, Param> {

  /**
   * Find some results according to queryParameters and feed them to consumer. If consumer returns false, stop.
   * @return false if the searching should be stopped immediately. This should happen only when consumer has returned false.
   */
  boolean execute(@NotNull Param queryParameters, @NotNull Processor<Result> consumer);
}
