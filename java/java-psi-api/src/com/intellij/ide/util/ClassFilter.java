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
package com.intellij.ide.util;

import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;

/**
* @author traff
*/
public interface ClassFilter {
  ClassFilter INSTANTIABLE = new ClassFilter() {
    public boolean isAccepted(PsiClass aClass) {
      return PsiUtil.isInstantiatable(aClass);
    }
  };

  boolean isAccepted(PsiClass aClass);
  ClassFilter ALL = new ClassFilter() {
    public boolean isAccepted(PsiClass aClass) {
      return true;
    }
  };

  interface ClassFilterWithScope extends ClassFilter {
    GlobalSearchScope getScope();
  }
}
