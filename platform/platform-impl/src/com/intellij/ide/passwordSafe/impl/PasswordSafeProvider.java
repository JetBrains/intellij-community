/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide.passwordSafe.impl;

import com.intellij.ide.passwordSafe.PasswordStorage;

/**
 * The provider for password safe component
 */
public abstract class PasswordSafeProvider implements PasswordStorage {
  /**
   * @return true, the implementation is supported in the current environment
   */
  public abstract boolean isSupported();
  /**
   * @return the description of the provider
   */
  public abstract String getDescription();

  /**
   * @return the name of provider
   */
  public abstract String getName();
}
