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

import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/5/11
 * Time: 7:54 PM
 */
public class ExceptionFilters {
  private ExceptionFilters() {
  }

  @NotNull
  public static List<Filter> getFilters(@NotNull GlobalSearchScope searchScope) {
    ExceptionFilterFactory[] extensions = ExceptionFilterFactory.EP_NAME.getExtensions();
    List<Filter> filters = new ArrayList<>(extensions.length);
    for (ExceptionFilterFactory extension : extensions) {
      filters.add(extension.create(searchScope));
    }
    return filters;
  }
}
