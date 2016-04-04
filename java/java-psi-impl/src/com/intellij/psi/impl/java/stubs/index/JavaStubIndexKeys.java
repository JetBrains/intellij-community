/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;
import com.intellij.psi.stubs.StubIndexKey;

/**
 * @author yole
 */
public class JavaStubIndexKeys {
  public static final StubIndexKey<String, PsiAnnotation> ANNOTATIONS = StubIndexKey.createIndexKey("java.annotations");
  public static final StubIndexKey<String, PsiReferenceList> SUPER_CLASSES = StubIndexKey.createIndexKey("java.class.extlist");
  public static final StubIndexKey<String, PsiField> FIELDS = StubIndexKey.createIndexKey("java.field.name");
  public static final StubIndexKey<String, PsiMethod> METHODS = StubIndexKey.createIndexKey("java.method.name");
  public static final StubIndexKey<String, PsiMember> JVM_STATIC_MEMBERS_NAMES = StubIndexKey.createIndexKey("jvm.static.member.name");
  public static final StubIndexKey<String, PsiMember> JVM_STATIC_MEMBERS_TYPES = StubIndexKey.createIndexKey("jvm.static.member.type");
  public static final StubIndexKey<String, PsiAnonymousClass> ANONYMOUS_BASEREF = StubIndexKey.createIndexKey("java.anonymous.baseref");
  public static final StubIndexKey<String, PsiMethod> METHOD_TYPES = StubIndexKey.createIndexKey("java.method.parameter.types");
  public static final StubIndexKey<String, PsiClass> CLASS_SHORT_NAMES = StubIndexKey.createIndexKey("java.class.shortname");
  public static final StubIndexKey<Integer,PsiClass> CLASS_FQN = StubIndexKey.createIndexKey("java.class.fqn");
  public static final StubIndexKey<IndexTree.Unit, PsiClassOwner> UNITS = StubIndexKey.createIndexKey("java.unit.tree");

  private JavaStubIndexKeys() {
  }
}
