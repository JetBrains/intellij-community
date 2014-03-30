/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.psi.PsiMember;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class JavaStaticMemberTypeIndex extends StringStubIndexExtension<PsiMember> {

  private static final JavaStaticMemberTypeIndex ourInstance = new JavaStaticMemberTypeIndex();
  public static JavaStaticMemberTypeIndex getInstance() {
    return ourInstance;
  }

  @NotNull
  @Override
  public StubIndexKey<String, PsiMember> getKey() {
    return JavaStubIndexKeys.JVM_STATIC_MEMBERS_TYPES;
  }

  public Collection<PsiMember> getStaticMembers(@NotNull final String shortTypeText, final Project project, @NotNull final GlobalSearchScope scope) {
    return StubIndex.getElements(JavaStubIndexKeys.JVM_STATIC_MEMBERS_TYPES, shortTypeText, project,
                                 new JavaSourceFilterScope(scope), PsiMember.class);
  }
}