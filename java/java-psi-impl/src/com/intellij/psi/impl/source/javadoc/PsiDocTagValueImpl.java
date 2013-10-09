/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.javadoc;

import com.intellij.psi.PsiReference;
import com.intellij.psi.javadoc.JavadocManager;
import com.intellij.psi.javadoc.JavadocTagInfo;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author mike
 */
public class PsiDocTagValueImpl extends CorePsiDocTagValueImpl {
  @Override
  public PsiReference getReference() {
    PsiDocTag docTag = PsiTreeUtil.getParentOfType(this, PsiDocTag.class);
    if (docTag == null) {
      return null;
    }
    final String name = docTag.getName();
    final JavadocManager manager = JavadocManager.SERVICE.getInstance(getProject());
    final JavadocTagInfo info = manager.getTagInfo(name);
    if (info == null) return null;

    return info.getReference(this);
  }
}
