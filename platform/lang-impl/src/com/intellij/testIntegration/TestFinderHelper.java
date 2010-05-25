/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;

import java.util.ArrayList;
import java.util.List;

public class TestFinderHelper {
  public static PsiElement findSourceElement(PsiElement from) {
    for (TestFinder each : getFinders()) {
      PsiElement result = each.findSourceElement(from);
      if (result != null) return result;
    }
    return null;
  }

  public static List<PsiElement> findTestsForClass(PsiElement element) {
    List<PsiElement> result = new ArrayList<PsiElement>();
    for (TestFinder each : getFinders()) {
      result.addAll(each.findTestsForClass(element));
    }
    /*if (!result.isEmpty())*/ result.add(null);
    return result;
  }

  public static List<PsiElement> findClassesForTest(PsiElement element) {
    List<PsiElement> result = new ArrayList<PsiElement>();
    for (TestFinder each : getFinders()) {
      result.addAll(each.findClassesForTest(element));
    }
    return result;
  }

  public static boolean isTest(PsiElement element) {
    if (element == null) return false;
    for (TestFinder each : getFinders()) {
      if (each.isTest(element)) return true;
    }
    return false;
  }

  private static TestFinder[] getFinders() {
    return Extensions.getExtensions(TestFinder.EP_NAME);
  }
}
