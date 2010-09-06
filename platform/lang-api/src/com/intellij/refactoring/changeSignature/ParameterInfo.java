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

package com.intellij.refactoring.changeSignature;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a parameter of a method affected by the "Change Signature" refactoring.
 *
 * @author yole
 * @since 8.1
 */
public interface ParameterInfo {
  /**
   * Returns the name of the parameter after the refactoring.
   *
   * @return parameter name.
   */
  String getName();

  /**
   * Returns the index of the parameter in the old parameter list, or -1 if the parameter
   * was added by the refactoring.
   *
   * @return old parameter index, or -1.
   */
  int getOldIndex();

  /**
   * For added parameters, returns the string representation of the default parameter value.
   *
   * @return default value, or null if the parameter wasn't added.
   */
  @Nullable
  String getDefaultValue();

  /**
   * Set parameter new name (to be changed to during refactoring)
   * @param name new name
   */
  void setName(String name);

  /**
   * Returns parameter type text
   *
   * @return type text
   */
  String getTypeText();

  /**
   * Flag whether refactoring should use any appropriate nearby variable as the default value
   *
   * @return flag value
   */
  boolean isUseAnySingleVariable();


  /**
   * Flag whether refactoring should use any appropriate nearby variable as the default value
   *
   * @param b new value
   */
  void setUseAnySingleVariable(boolean b);
}
