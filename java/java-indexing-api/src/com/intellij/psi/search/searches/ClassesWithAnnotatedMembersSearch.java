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
package com.intellij.psi.search.searches;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * Searcher that searches for classes which have members annotated with the specified annotation.
 *
 * @author yole
 */
public class ClassesWithAnnotatedMembersSearch extends ExtensibleQueryFactory<PsiClass, ClassesWithAnnotatedMembersSearch.Parameters> {
  public static final ExtensionPointName<QueryExecutor> EP_NAME = ExtensionPointName.create("com.intellij.classesWithAnnotatedMembersSearch");
  public static final ClassesWithAnnotatedMembersSearch INSTANCE = new ClassesWithAnnotatedMembersSearch();

  public static class Parameters {
    private final PsiClass myAnnotationClass;
    private final SearchScope myScope;

    public Parameters(final PsiClass annotationClass, final SearchScope scope) {
      myAnnotationClass = annotationClass;
      myScope = scope;
    }

    public PsiClass getAnnotationClass() {
      return myAnnotationClass;
    }

    public SearchScope getScope() {
      return myScope;
    }
  }

  public static Query<PsiClass> search(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
    return INSTANCE.createQuery(new Parameters(annotationClass, scope));
  }
}
