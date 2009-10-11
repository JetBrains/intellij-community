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

/*
 * @author: Eugene Zhuravlev
 * Date: Jul 10, 2003
 * Time: 4:51:25 PM
 */
package com.intellij.compiler.make;

import com.intellij.openapi.compiler.CompilerBundle;

public class CacheCorruptedException extends Exception{
  private static final String DEFAULT_MESSAGE = CompilerBundle.message("error.dependency.info.on.disk.corrupted");
  public CacheCorruptedException(String message) {
    super((message == null || message.length() == 0)? DEFAULT_MESSAGE : message);
  }

  public CacheCorruptedException(Throwable cause) {
    super(DEFAULT_MESSAGE, cause);
  }

  public CacheCorruptedException(String message, Throwable cause) {
    super((message == null || message.length() == 0)? DEFAULT_MESSAGE : message, cause);
  }

  public String getMessage() {
    return super.getMessage();
  }
}
