/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.execution.filters;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * A factory for console filters that allow highlight exception stacktraces with links to the corresponding code.
 */
public interface ExceptionFilterFactory {
  ExtensionPointName<ExceptionFilterFactory> EP_NAME = ExtensionPointName.create("com.intellij.exceptionFilter");

  /**
   * @param searchScope context-specific search scope (e.g., to search classes mentioned in the exception stack trace)
   * @return a {@link Filter} instance that produces highlighting information.
   */
  @NotNull
  Filter create(@NotNull GlobalSearchScope searchScope);

  /**
   * @param project current project
   * @param searchScope context-specific search scope (e.g., to search classes mentioned in the exception stack trace)
   * @return a {@link Filter} instance that produces highlighting information.
   * Override this method if the project is important. In this case, {@link #create(GlobalSearchScope)} implementation
   * may delegate to this method with {@code Objects.requireNonNull(searchScope.getProject()), searchScope} parameters.
   */
  default Filter create(@NotNull Project project, @NotNull GlobalSearchScope searchScope) {
    return create(searchScope);
  }
}
