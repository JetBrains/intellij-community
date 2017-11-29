/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.java.stubs.JavaClassElementType;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.BitUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class PsiClassStubImpl<T extends PsiClass> extends StubBase<T> implements PsiClassStub<T> {
  private static final int DEPRECATED = 0x01;
  private static final int INTERFACE = 0x02;
  private static final int ENUM = 0x04;
  private static final int ENUM_CONSTANT_INITIALIZER = 0x08;
  private static final int ANONYMOUS = 0x10;
  private static final int ANON_TYPE = 0x20;
  private static final int IN_QUALIFIED_NEW = 0x40;
  private static final int DEPRECATED_ANNOTATION = 0x80;
  private static final int ANONYMOUS_INNER = 0x100;
  private static final int LOCAL_CLASS_INNER = 0x200;

  private final String myQualifiedName;
  private final String myName;
  private final String myBaseRefText;
  private final short myFlags;
  private String mySourceFileName;

  public PsiClassStubImpl(final JavaClassElementType type,
                          final StubElement parent,
                          @Nullable final String qualifiedName,
                          @Nullable final String name,
                          @Nullable final String baseRefText,
                          final short flags) {
    super(parent, type);
    myQualifiedName = qualifiedName;
    myName = name;
    myBaseRefText = baseRefText;
    myFlags = flags;
    if (StubBasedPsiElementBase.ourTraceStubAstBinding) {
      String creationTrace = "Stub creation thread: " + Thread.currentThread() + "\n" + DebugUtil.currentStackTrace();
      putUserData(StubBasedPsiElementBase.CREATION_TRACE, creationTrace);
    }
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public String getQualifiedName() {
    return myQualifiedName;
  }

  @Override
  public String getBaseClassReferenceText() {
    return myBaseRefText;
  }

  @Override
  public boolean isDeprecated() {
    return BitUtil.isSet(myFlags, DEPRECATED);
  }

  @Override
  public boolean hasDeprecatedAnnotation() {
    return BitUtil.isSet(myFlags, DEPRECATED_ANNOTATION);
  }

  @Override
  public boolean isInterface() {
    return BitUtil.isSet(myFlags, INTERFACE);
  }

  @Override
  public boolean isEnum() {
    return BitUtil.isSet(myFlags, ENUM);
  }

  @Override
  public boolean isEnumConstantInitializer() {
    return isEnumConstInitializer(myFlags);
  }

  public static boolean isEnumConstInitializer(final short flags) {
    return BitUtil.isSet(flags, ENUM_CONSTANT_INITIALIZER);
  }

  @Override
  public boolean isAnonymous() {
    return isAnonymous(myFlags);
  }

  public static boolean isAnonymous(final short flags) {
    return BitUtil.isSet(flags, ANONYMOUS);
  }

  @Override
  public boolean isAnnotationType() {
    return BitUtil.isSet(myFlags, ANON_TYPE);
  }

  @Override
  public LanguageLevel getLanguageLevel() {
    StubElement parent = getParentStub();
    if (parent instanceof PsiJavaFileStub) {
      LanguageLevel level = ((PsiJavaFileStub)parent).getLanguageLevel();
      if (level != null) {
        return level;
      }
    }
    return LanguageLevel.HIGHEST;
  }

  @Override
  public String getSourceFileName() {
    return mySourceFileName;
  }

  public void setSourceFileName(String sourceFileName) {
    mySourceFileName = sourceFileName;
  }

  @Override
  public boolean isAnonymousInQualifiedNew() {
    return BitUtil.isSet(myFlags, IN_QUALIFIED_NEW);
  }

  public short getFlags() {
    return myFlags;
  }

  public static short packFlags(boolean isDeprecated,
                               boolean isInterface,
                               boolean isEnum,
                               boolean isEnumConstantInitializer,
                               boolean isAnonymous,
                               boolean isAnnotationType,
                               boolean isInQualifiedNew,
                               boolean hasDeprecatedAnnotation, 
                               boolean anonymousInner,
                               boolean localClassInner
                                ) {
    short flags = 0;
    if (isDeprecated) flags |= DEPRECATED;
    if (isInterface) flags |= INTERFACE;
    if (isEnum) flags |= ENUM;
    if (isEnumConstantInitializer) flags |= ENUM_CONSTANT_INITIALIZER;
    if (isAnonymous) flags |= ANONYMOUS;
    if (isAnnotationType) flags |= ANON_TYPE;
    if (isInQualifiedNew) flags |= IN_QUALIFIED_NEW;
    if (hasDeprecatedAnnotation) flags |= DEPRECATED_ANNOTATION;
    if (anonymousInner) flags |= ANONYMOUS_INNER;
    if (localClassInner) flags |= LOCAL_CLASS_INNER;
    return flags;
  }

  public boolean isAnonymousInner() {
    return BitUtil.isSet(myFlags, ANONYMOUS_INNER);
  }
  public boolean isLocalClassInner() {
    return BitUtil.isSet(myFlags, LOCAL_CLASS_INNER);
  }
  
  @Override
  @SuppressWarnings("SpellCheckingInspection")
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("PsiClassStub[");

    if (isInterface()) {
      builder.append("interface ");
    }

    if (isAnonymous()) {
      builder.append("anonymous ");
    }

    if (isEnum()) {
      builder.append("enum ");
    }

    if (isAnnotationType()) {
      builder.append("annotation ");
    }

    if (isEnumConstantInitializer()) {
      builder.append("enumInit ");
    }

    if (isDeprecated()) {
      builder.append("deprecated ");
    }

    if (hasDeprecatedAnnotation()) {
      builder.append("deprecatedA ");
    }

    builder.append("name=").append(getName()).append(" fqn=").append(getQualifiedName());

    if (getBaseClassReferenceText() != null) {
      builder.append(" baseref=").append(getBaseClassReferenceText());
    }

    if (isAnonymousInQualifiedNew()) {
      builder.append(" inqualifnew");
    }

    if (isAnonymousInner()) {
      builder.append(" jvmAnonymousInner");
    }

    if (isLocalClassInner()) {
      builder.append(" jvmLocalClassInner");
    }

    builder.append("]");

    return builder.toString();
  }
}