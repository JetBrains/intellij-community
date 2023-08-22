/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.jps.builders.storage;

import java.io.IOException;

/**
 * This exception indicates that some internal build storage cannot be loaded or saved properly. Rebuild will be requested to recover from
 * the corruption.
 */
public class BuildDataCorruptedException extends RuntimeException {
  public BuildDataCorruptedException(IOException cause) {
    super(cause);
  }

  public BuildDataCorruptedException(String message) {
    super(message);
  }

  @Override
  public synchronized Throwable initCause(Throwable cause) {
    throw new UnsupportedOperationException("Overwriting of cause field is not supported for " + BuildDataCorruptedException.class.getName());
  }

  @Override
  public synchronized IOException getCause() {
    return (IOException)super.getCause();
  }
}
