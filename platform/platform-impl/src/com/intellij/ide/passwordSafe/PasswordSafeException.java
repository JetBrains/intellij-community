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
package com.intellij.ide.passwordSafe;

/**
 * The exception that is thrown when password safe is not available (unable to ask for master password)
 */
public class PasswordSafeException extends Exception {

  private static final long MIN_INTERVAL = 1000L;

  private long myTimeMillis = System.currentTimeMillis();

  public PasswordSafeException(String message, Throwable cause) {
    super(message, cause);
  }

  public PasswordSafeException(String message) {
    super(message);
  }

  public long getTimeMillis() {
    return myTimeMillis;
  }

  public boolean justHappened() {
    long timeMillis = System.currentTimeMillis();
    if (timeMillis - myTimeMillis < MIN_INTERVAL) {
      myTimeMillis = timeMillis;
      return true;
    }
    return false;
  }
}
