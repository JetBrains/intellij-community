// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.util.xml.CanonicalPsiTypeConverterImpl;
import com.intellij.util.xml.PsiClassConverter;

final class JavaDomConverterManagerImpl extends ConverterManagerImpl {
  JavaDomConverterManagerImpl() {
    addConverter(PsiClass.class, new PsiClassConverter());
    addConverter(PsiType.class, new CanonicalPsiTypeConverterImpl());
  }
}
