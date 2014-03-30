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
package org.jetbrains.jps;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.EmptyRunnable;

/**
 * @author nik
 */
public class TimingLog {
  public static final Logger LOG = Logger.getInstance(TimingLog.class);

  public static Runnable startActivity(final String name) {
    if (!LOG.isDebugEnabled()) {
      return EmptyRunnable.INSTANCE;
    }
    final long start = System.currentTimeMillis();
    return new Runnable() {
      @Override
      public void run() {
        LOG.debug(name + " in " + (System.currentTimeMillis() - start) + "ms");
      }
    };
  }
}
