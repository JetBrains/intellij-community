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
package com.intellij.ide.util.frameworkSupport;

import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class FrameworkRole {

  public static FrameworkRole[] UNKNOWN = new FrameworkRole[0];

  /* Groovy etc. */
//  public static FrameworkRole JVM_LANGUAGES = new FrameworkRole();

  /** servlet-based frameworks like Struts, Tapestry etc. */
  public static FrameworkRole JEE_FRAMEWORKS = new FrameworkRole("javaee");

  private final String myId;

  public FrameworkRole(@NotNull String id) {
    myId = id;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof FrameworkRole && myId.equals(((FrameworkRole)obj).myId);
  }

  @Override
  public String toString() {
    return myId;
  }
}
