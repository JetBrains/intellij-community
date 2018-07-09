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
package org.jetbrains.jps.builders.java.dependencyView;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public abstract class InternedString {
  @NotNull
  public abstract DependencyContext getContext();

  public abstract int asInt();

  public abstract String asString();

  public static InternedString create(final DependencyContext context, final String val) {
    return new InternedString() {
      @Override
      public DependencyContext getContext() {
        return context;
      }

      @Override
      public int asInt() {
        return context.get(val);
      }

      @Override
      public String asString() {
        return val;
      }
    };
  }

  public static InternedString create(final DependencyContext context, final int val) {
    return new InternedString() {
      @NotNull
      @Override
      public DependencyContext getContext() {
        return context;
      }

      @Override
      public int asInt() {
        return val;
      }

      @Override
      public String asString() {
        return getContext().getValue(val);
      }
    };
  }
}
