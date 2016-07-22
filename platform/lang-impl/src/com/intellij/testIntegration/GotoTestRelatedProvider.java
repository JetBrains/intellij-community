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
package com.intellij.testIntegration;

import com.intellij.navigation.GotoRelatedItem;
import com.intellij.navigation.GotoRelatedProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class GotoTestRelatedProvider extends GotoRelatedProvider {
  @NotNull
  @Override
  public List<? extends GotoRelatedItem> getItems(@NotNull DataContext context) {
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(context);
    if (file == null) return Collections.emptyList();

    Collection<PsiElement> result;
    final boolean isTest = TestFinderHelper.isTest(file);
    if (isTest) {
      result = TestFinderHelper.findClassesForTest(file);
    }
    else {
      result = TestFinderHelper.findTestsForClass(file);
    }

    if (!result.isEmpty()) {
      final List<GotoRelatedItem> items = new ArrayList<>();
      for (PsiElement element : result) {
        items.add(new GotoRelatedItem(element, isTest ? "Tests" : "Testee classes"));
      }
      return items;
    }
    return Collections.emptyList();
  }
}
