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
package com.intellij.rt.debugger.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author egor
 */
public class CaptureStorage {
  private static final int MAX_STORED_STACKS = 1000;

  public static final Map<Object, Exception> STORAGE = Collections.synchronizedMap(new LinkedHashMap<Object, Exception>() {
    @Override
    protected boolean removeEldestEntry(Map.Entry eldest) {
      return size() > MAX_STORED_STACKS;
    }
  });

  // to be run from the debugger
  @SuppressWarnings("unused")
  public static Object[][] getRelatedStack(Object key) {
    Exception exception = STORAGE.get(key);
    StackTraceElement[] stackTrace = exception.getStackTrace();
    Object[][] res = new Object[stackTrace.length][];
    for (int i = 0; i < stackTrace.length; i++) {
      StackTraceElement elem = stackTrace[i];
      res[i] = new Object[]{elem.getClassName(), elem.getFileName(), elem.getMethodName(), String.valueOf(elem.getLineNumber())};
    }
    return res;
  }
}
