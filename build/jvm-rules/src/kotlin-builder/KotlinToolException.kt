/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.bazel.jvm.kotlin

internal sealed class KotlinToolException(
  msg: String,
  ex: Throwable? = null,
) : RuntimeException(msg, ex)

internal class CompilationException(
  msg: String,
  cause: Throwable? = null,
) : KotlinToolException(msg, cause)

internal class CompilationStatusException(
  msg: String,
  val status: Int,
) : KotlinToolException(msg)
