/*
 * Copyright 2001-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate.config;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface that defines a policy for dealing with where to insert a new <code>toString()</code>
 * method in the javafile.
 */
public interface InsertNewMethodStrategy {

  /**
   * Applies the chosen policy.
   *
   * @param clazz     PSIClass.
   * @param newMethod new method.
   * @param editor
   * @return if the policy was executed normally (not cancelled)
   */
  @Nullable
  PsiMethod insertNewMethod(PsiClass clazz, @NotNull PsiMethod newMethod, Editor editor);
}
