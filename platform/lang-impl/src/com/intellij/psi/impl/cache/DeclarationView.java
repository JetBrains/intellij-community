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

package com.intellij.psi.impl.cache;



/**
 * @author max
 */
public interface DeclarationView extends RepositoryItemView {
  int getModifiers(long id);
  boolean isDeprecated(long id);
  boolean mayBeDeprecatedByAnnotation(long id); //for source elements only, for cls the value of the attribute is written 
  long[] getInnerClasses(long id);
  String[] getAnnotations (long id);
}
