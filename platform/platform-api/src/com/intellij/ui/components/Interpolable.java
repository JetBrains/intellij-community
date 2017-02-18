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
package com.intellij.ui.components;

/**
 * An entity with a value that is adjusted asynchronously.
 * <p>
 * Explicitly separates current / target values.
 */
public interface Interpolable {
  /**
   * Gets the current value.
   * <p>
   * Technically, it's "getCurrentValue", but we need to match the name in Swing classes.
   *
   * @return the current value.
   */
  int getValue();

  /**
   * Sets the current value (synchronously).
   *
   * @param value the current value
   */
  void setCurrentValue(int value);

  /**
   * Gets the target value.
   *
   * @return the target value.
   */
  int getTargetValue();

  /**
   * Sets the target value.
   * <p>
   * Technically, it's "setTargetValue", but we need to match the name in Swing classes.
   *
   * @param value the target value
   */
  void setValue(int value);
}
