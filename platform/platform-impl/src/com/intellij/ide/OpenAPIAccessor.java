/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import sun.misc.Unsafe;

import java.awt.*;

/**
 * Provides ability to access ApplicationImpl package-private methods
 * without reflection
 */
public final class OpenAPIAccessor {


  private OpenAPIAccessor() {}

  private static ApplicationImplAccessor applicationImplAccessor;
  private static final Unsafe unsafe = AtomicFieldUpdater.getUnsafe();

  public interface ApplicationImplAccessor {

    boolean applyActivation (ApplicationImpl app, Window window);

    boolean applyDeactivation (ApplicationImpl app, Window window);

    boolean applyDelayedDeactivation (ApplicationImpl app, Window window);

  }

  /*
   * Set an accessor object for the com.intellij.openapi.application.impl.ApplicationImpl class.
   */
  public static void setApplicationImplAccessor(ApplicationImplAccessor aia) {
    applicationImplAccessor = aia;
  }

  /*
   * Retrieve the accessor object for the com.intellij.openapi.application.impl.ApplicationImpl class.
   */
  public static ApplicationImplAccessor getApplicationImplAccessor() {
    if (applicationImplAccessor == null) {
      unsafe.ensureClassInitialized(ApplicationImpl.class);
    }

    return applicationImplAccessor;
  }

}
