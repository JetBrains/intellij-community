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
 * @author max
 */
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.impl.search.JavaLikeSourceFilterScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;

import java.util.Collection;

public class JavaSuperClassNameOccurenceIndex extends StringStubIndexExtension<PsiReferenceList> {
  public static final StubIndexKey<String, PsiReferenceList> KEY = StubIndexKey.createIndexKey("java.class.extlist");
  private static final int VERSION = 1;

  private static final JavaSuperClassNameOccurenceIndex ourInstance = new JavaSuperClassNameOccurenceIndex();
  public static JavaSuperClassNameOccurenceIndex getInstance() {
    return ourInstance;
  }

  public StubIndexKey<String, PsiReferenceList> getKey() {
    return KEY;
  }

  public Collection<PsiReferenceList> get(final String s, final Project project, final GlobalSearchScope scope) {
    return super.get(s, project, new JavaLikeSourceFilterScope(scope, project));
  }

  @Override
  public int getVersion() {
    return super.getVersion() + VERSION;
  }
}
