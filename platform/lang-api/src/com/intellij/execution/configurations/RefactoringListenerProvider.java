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
package com.intellij.execution.configurations;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import org.jetbrains.annotations.Nullable;

/**
 * This interface can be implemented by run configurations that need to update their settings when the target element is renamed or
 * moved (for example, a Java run configuration needs to update the class name stored in its settings when the class is renamed).
 * Note that if you provide a listener, and the run configuration has a generated name, the name will be automatically updated after
 * the refactoring.
 *
 * @author spleaner
 */
public interface RefactoringListenerProvider {

  /**
   * Returns a listener to handle a rename or move refactoring of the specified PSI element.
   *
   * @param element the element on which a refactoring was invoked.
   * @return the listener to handle the refactoring, or null if the run configuration doesn't care about refactoring of this element.
   */
  @Nullable
  RefactoringElementListener getRefactoringElementListener(final PsiElement element);

}
