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
package com.intellij.ide.passwordSafe.impl.providers.nil;

import com.intellij.openapi.project.Project;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.ide.passwordSafe.impl.PasswordSafeProvider;

/**
 * The most secure provider that does not store anything, so it cannot be cracked
 */
public final class NilProvider extends PasswordSafeProvider {
  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isSupported() {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getDescription() {
    return "The provider that does not remembers password.";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getName() {
    return "Do not Store";
  }

  /**
   * {@inheritDoc}
   */
  public String getPassword(Project project, Class requester, String key) throws PasswordSafeException {
    // nothing is stored
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public void removePassword(Project project, Class requester, String key) throws PasswordSafeException {
    // do nothing
  }

  /**
   * {@inheritDoc}
   */
  public void storePassword(Project project, Class requester, String key, String value) throws PasswordSafeException {
    // just forget about password
  }
}
