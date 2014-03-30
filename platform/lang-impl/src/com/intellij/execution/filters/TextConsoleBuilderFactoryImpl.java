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
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 04.08.2006
 * Time: 17:57:56
 */
package com.intellij.execution.filters;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author dyoma
 */
public class TextConsoleBuilderFactoryImpl extends TextConsoleBuilderFactory {
  @Override
  public TextConsoleBuilder createBuilder(@NotNull final Project project) {
    return new TextConsoleBuilderImpl(project);
  }

  @Override
  public TextConsoleBuilder createBuilder(@NotNull Project project, @NotNull GlobalSearchScope scope) {
    return new TextConsoleBuilderImpl(project, scope);
  }
}