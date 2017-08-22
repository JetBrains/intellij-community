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
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import groovy.transform.Immutable

/**
 * @author nik
 */
@CompileStatic
@Immutable
class LogMessage {
  enum Kind {
    ERROR, WARNING, INFO, PROGRESS, BLOCK_STARTED, BLOCK_FINISHED, ARTIFACT_BUILT, COMPILATION_ERROR, STATISTICS
  }
  final Kind kind
  final String text
}