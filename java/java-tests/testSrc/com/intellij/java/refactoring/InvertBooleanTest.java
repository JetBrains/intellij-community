/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.refactoring;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.invertBoolean.InvertBooleanProcessor;
import com.intellij.testFramework.TestDataPath;

/**
 * @author ven
 */
@TestDataPath("$CONTENT_ROOT/testData/refactoring/invertBoolean/")
public class InvertBooleanTest extends LightRefactoringParameterizedTestCase {
  @Override
  protected void perform() {
    PsiElement element = TargetElementUtil.findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue(element instanceof PsiNamedElement);

    final PsiNamedElement namedElement = (PsiNamedElement)element;
    final String name = namedElement.getName();
    new InvertBooleanProcessor(namedElement, name + "Inverted").run();
  }

  @Override
  protected String getAfterFile(String fileName) {
    return FileUtilRt.getNameWithoutExtension(fileName) + "_" + AFTER_PREFIX + "." + FileUtilRt.getExtension(fileName);
  }

  @Override
  protected String getBeforeFile(String fileName) {
    return fileName;
  }

  @Override
  public String getFileSuffix(String beforeFile) {
    return !beforeFile.contains(AFTER_PREFIX) && !beforeFile.endsWith(CONFLICTS_SUFFIX) ? beforeFile : null;
  }
}
