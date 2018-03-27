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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.util.ObjectUtils;

import java.util.Set;

public class ImportsHighlightUtil {
  public static final Key<Set<String>> IMPORTS_FROM_TEMPLATE = Key.create("IMPORT_FROM_FILE_TEMPLATE");

  static HighlightInfo checkStaticOnDemandImportResolvesToClass(PsiImportStaticStatement statement) {
    if (statement.isOnDemand() && statement.resolveTargetClass() == null) {
      PsiJavaCodeReferenceElement ref = statement.getImportReference();
      if (ref != null) {
        PsiElement resolve = ref.resolve();
        if (resolve != null) {
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(ObjectUtils.notNull(ref.getReferenceNameElement(), ref))
            .descriptionAndTooltip("Class " + ref.getCanonicalText() + " not found").create();
        }
      }
    }
    return null;
  }
}
