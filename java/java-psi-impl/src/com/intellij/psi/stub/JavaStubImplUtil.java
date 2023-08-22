// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stub;

import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.PsiMemberStub;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.impl.source.StubbedSpine;
import com.intellij.psi.impl.source.tree.JavaElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaStubImplUtil {
  public static int getMethodStubIndex(PsiMethod method) {
    if (!(method instanceof PsiMethodImpl)) return -1;
    PsiFileImpl file = (PsiFileImpl)method.getContainingFile();
    StubbedSpine spine = file.getStubbedSpine();

    int result = 0;
    for (int i = 0; i < spine.getStubCount(); i++) {
      if (spine.getStubType(i) == JavaElementType.METHOD) {
        if (spine.getStubPsi(i) == method) {
          return result;
        }
        result++;
      }
    }
    return -1;
  }

  public static <T extends PsiMember & PsiJavaDocumentedElement> boolean isMemberDeprecated(@NotNull T member,
                                                                                            @Nullable PsiMemberStub<?> stub) {
    if (stub != null) {
      return stub.isDeprecated() || stub.hasDeprecatedAnnotation() && PsiImplUtil.isDeprecatedByAnnotation(member);
    }

    return PsiImplUtil.isDeprecatedByDocTag(member) || PsiImplUtil.isDeprecatedByAnnotation(member);
  }
}
