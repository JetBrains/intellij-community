/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.indexing;

/**
 * Marker interface of index that is built from PSI, meaning its invalidation will happen upon producing new PSI.
 *
 * @deprecated by default all content-aware indices are PSI dependent. Use {@link DocumentChangeDependentIndex} to make index PSI-independent (since 2019.2)
 */
@Deprecated
public interface PsiDependentIndex {
}
