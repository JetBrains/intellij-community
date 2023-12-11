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
package com.intellij.openapi.components;

public enum RoamingType {
  /**
   * Stored only locally, not shared and not exportable
   */
  DISABLED,

  /**
   * Stored locally, not exportable, but can be migrated to another IDE on the same machine (Migrate Settings)
   */
  LOCAL,

  /**
   * Stored per operating system (Mac OS X, Linux, FreeBSD, Unix, Windows)
   */
  PER_OS,

  /**
   * Default, shared.
   */
  DEFAULT

  ;

  /**
   * Indicates whether this setting can be transferred to another IDE on the same machine
   * @return <strong>true</strong> if this setting can be transferred to another IDE on the same machine,
   *          <br/><strong>false</strong> otherwise
   */
  public boolean canBeMigrated() {
    return this != DISABLED;
  }

  /**
   * Indicates whether this setting can be exported and shared with other IDEs on other machines
   *
   * @return <strong>true</strong> if this setting can be exported and shared with other IDEs on other machines,
   *          <br/><strong>false</strong> otherwise
   */
  public boolean isShared() {
    return this == DEFAULT || this == PER_OS;
  }

  /**
   * Indicates whether this setting is shared only between machines with the same OS type (i.e. Windows, Linux, macOS, etc.)
   *
   * @return <strong>true</strong> if this setting is shared only between machines with the same OS type
   *          <br/><strong>false</strong> otherwise
   */
  public boolean isOSSpefic() {
    return this == PER_OS;
  }
}
