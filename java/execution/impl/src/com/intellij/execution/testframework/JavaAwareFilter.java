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
 * User: anna
 * Date: 20-Feb-2008
 */
package com.intellij.execution.testframework;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;

public class JavaAwareFilter {
  private JavaAwareFilter() {
  }

  public static Filter METHOD(final Project project, final GlobalSearchScope searchScope) {
    return new Filter() {
      public boolean shouldAccept(final AbstractTestProxy test) {
        final Location location = test.getLocation(project, searchScope);
        if (location instanceof MethodLocation) return true;
        if (location instanceof PsiLocation && location.getPsiElement() instanceof PsiMethod) return true;
        return false;
      }
    };
  }
}