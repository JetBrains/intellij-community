/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic

/**
 * @see org.jetbrains.intellij.build.impl.PluginLayout.PluginLayoutSpec#withGeneratedResources
 * @author nik
 */
@CompileStatic
interface ResourcesGenerator {
  /**
   * Generate files which need to be included into the product distribution somewhere under {@code context.paths.temp} directory.
   * @return path to the generated file or directory or {@code null} if nothing was generated
   */
  File generateResources(BuildContext context)
}