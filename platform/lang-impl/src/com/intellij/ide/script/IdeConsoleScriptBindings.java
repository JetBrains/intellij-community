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
package com.intellij.ide.script;

import com.intellij.openapi.project.Project;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IdeConsoleScriptBindings {

  public static final Binding<IDE> IDE = Binding.create("IDE", IDE.class);

  public static void ensureIdeIsBound(@Nullable Project project, @NotNull IdeScriptEngine engine) {
    IDE oldIdeBinding = IDE.get(engine);
    if (oldIdeBinding == null) {
      IDE.set(engine, new IDE(project, engine));
    }
  }

  private IdeConsoleScriptBindings() {
  }

  public static class Binding<T> {
    private final String myName;
    private final Class<T> myClass;

    private Binding(@NotNull String name, @NotNull Class<T> clazz) {
      myName = name;
      myClass = clazz;
    }

    public void set(@NotNull IdeScriptEngine engine, T value) {
      engine.setBinding(myName, value);
    }

    public T get(@NotNull IdeScriptEngine engine) {
      return ObjectUtils.tryCast(engine.getBinding(myName), myClass);
    }

    static <T> Binding<T> create(@NotNull String name, @NotNull Class<T> clazz) {
      return new Binding<>(name, clazz);
    }
  }
}
