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
package com.intellij.psi;

import java.util.List;

/**
 * Represents a resource list of try-with-resources statement (automatic resource management) introduced in JDK 7.
 *
 * @see PsiTryStatement#getResourceList()
 * @since 10.5
 */
public interface PsiResourceList extends PsiElement, Iterable<PsiResourceListElement> {
  int getResourceVariablesCount();

  /** @deprecated use {@link #iterator()} (to be removed in IDEA 17) */
  @SuppressWarnings("unused")
  List<PsiResourceVariable> getResourceVariables();
}
