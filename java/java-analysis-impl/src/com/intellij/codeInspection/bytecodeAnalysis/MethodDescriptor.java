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
package com.intellij.codeInspection.bytecodeAnalysis;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.MessageDigest;

/**
 * An uniquely method (class name+method name+signature) identifier: either {@link Method} or {@link HMethod}.
 */
public interface MethodDescriptor {
  /**
   * Creates and returns the hashed representation of this method descriptor.
   * May return itself if already hashed. Note that hashed descriptor is not equal to
   * non-hashed one.
   *
   * @param md message digest to use for hashing (could be null to use the default one)
   * @return a corresponding HMethod.
   */
  @NotNull HMethod hashed(@Nullable MessageDigest md);
}
