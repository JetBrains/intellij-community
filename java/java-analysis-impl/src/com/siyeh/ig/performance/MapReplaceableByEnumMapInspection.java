/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class MapReplaceableByEnumMapInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("map.replaceable.by.enum.map.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MapReplaceableByEnumMapVisitor();
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    if (infos.length != 1) return null;
    PsiLocalVariable localVariable = (PsiLocalVariable)infos[0];
    PsiType[] parameters = CollectionReplaceableByEnumCollectionVisitor.extractParameterType(localVariable, 2);
    if (parameters == null) return null;
    PsiType enumParameter = parameters[0];
    String parameterListText = Arrays.stream(parameters).map(PsiType::getCanonicalText).collect(Collectors.joining(",", "<", ">"));
    PsiClass probablyEnum = PsiUtil.resolveClassInClassTypeOnly(enumParameter);
    if (probablyEnum == null || !probablyEnum.isEnum()) return null;
    String text = "new java.util.EnumMap" + parameterListText + "(" + enumParameter.getCanonicalText() + ".class)";
    return new ReplaceExpressionWithTextFix(text, CommonQuickFixBundle.message("fix.replace.with.x", "EnumMap"));
  }

  private static class MapReplaceableByEnumMapVisitor extends CollectionReplaceableByEnumCollectionVisitor {

    @Override
    @NotNull
    protected List<String> getUnreplaceableCollectionNames() {
      return Arrays.asList(CommonClassNames.JAVA_UTIL_CONCURRENT_HASH_MAP, "java.util.concurrent.ConcurrentSkipListMap",
                           "java.util.LinkedHashMap");
    }

    @NotNull
    @Override
    protected List<String> getReplaceableCollectionNames() {
      return Collections.singletonList(CommonClassNames.JAVA_UTIL_HASH_MAP);
    }

    @Override
    @NotNull
    protected String getReplacementCollectionName() {
      return "java.util.EnumMap";
    }

    @Override
    @NotNull
    protected String getBaseCollectionName() {
      return CommonClassNames.JAVA_UTIL_MAP;
    }

  }
}
