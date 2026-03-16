// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.stubs.StubIndexKey;

public final class JavaStubIndexKeys {
  public static final StubIndexKey<String, PsiAnnotation> ANNOTATIONS = StubIndexKey.createIndexKey("java.annotations");
  public static final StubIndexKey<String, PsiReferenceList> SUPER_CLASSES = StubIndexKey.createIndexKey("java.class.extlist");
  public static final StubIndexKey<String, PsiField> FIELDS = StubIndexKey.createIndexKey("java.field.name");
  public static final StubIndexKey<String, PsiRecordComponent> RECORD_COMPONENTS = StubIndexKey.createIndexKey("java.record.component.name");
  public static final StubIndexKey<String, PsiMethod> METHODS = StubIndexKey.createIndexKey("java.method.name");
  public static final StubIndexKey<String, PsiMember> JVM_STATIC_MEMBERS_NAMES = StubIndexKey.createIndexKey("jvm.static.member.name");
  public static final StubIndexKey<String, PsiMember> JVM_STATIC_MEMBERS_TYPES = StubIndexKey.createIndexKey("jvm.static.member.type");
  public static final StubIndexKey<String, PsiAnonymousClass> ANONYMOUS_BASEREF = StubIndexKey.createIndexKey("java.anonymous.baseref");
  public static final StubIndexKey<String, PsiMethod> METHOD_TYPES = StubIndexKey.createIndexKey("java.method.parameter.types");
  public static final StubIndexKey<String, PsiClass> CLASS_SHORT_NAMES = StubIndexKey.createIndexKey("java.class.shortname");
  public static final StubIndexKey<CharSequence, PsiClass> CLASS_FQN = StubIndexKey.createIndexKey("java.class.fqn");
  public static final StubIndexKey<String, PsiJavaModule> MODULE_NAMES = StubIndexKey.createIndexKey("java.module.name");
  public static final StubIndexKey<String, PsiImplicitClass> IMPLICIT_CLASSES = StubIndexKey.createIndexKey("java.unnamed.class"); //preserve old name for index

  private JavaStubIndexKeys() { }
}