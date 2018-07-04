/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.SimpleJavaParameters;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 4/17/13 11:32 AM
 */
public interface ParametersEnhancer {

  /**
   * Our recommended practice is to work with third-party api from external process in order to avoid potential problems with
   * the whole ide process. For example, the api might contain a memory leak which crashed the whole process etc.
   * <p/>
   * This method is a callback which allows particular external system integration to adjust that external process
   * settings. Most of the time that means classpath adjusting.
   *
   * @param parameters parameters to be applied to the slave process which will be used for external system communication
   */
  void enhanceRemoteProcessing(@NotNull SimpleJavaParameters parameters) throws ExecutionException;
}