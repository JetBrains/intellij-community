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
package com.intellij.util;

import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class InstanceofCheckerGenerator {
  private static final InstanceofCheckerGenerator ourInstance;

  static {
    try {
      ourInstance = (InstanceofCheckerGenerator)Class.forName("com.intellij.util.InstanceofCheckerGeneratorImpl").newInstance();
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  public static InstanceofCheckerGenerator getInstance() {
    return ourInstance;
  }

  @NotNull 
  public abstract Condition<Object> getInstanceofChecker(Class<?> someClass);

}
