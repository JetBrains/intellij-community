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
package com.intellij.compiler.make;

import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 7, 2004
 */
public interface RetentionPolicies {
  /**
   * Annotations are to be discarded by the compiler.
   */
  int SOURCE = 0x1;

  @NonNls String SOURCE_STR = "SOURCE";

  /**
   * Annotations are to be recorded in the class file by the compiler
   * but need not be retained by the VM at run time.  This is the default
   * behavior.
   */
  int CLASS = 0x2;

  @NonNls String CLASS_STR = "CLASS";

  /**
   * Annotations are to be recorded in the class file by the compiler and
   * retained by the VM at run time, so they may be read reflectively.
   */
  int RUNTIME = 0x4;

  @NonNls String RUNTIME_STR = "RUNTIME";

  int ALL = SOURCE | CLASS | RUNTIME;
}
