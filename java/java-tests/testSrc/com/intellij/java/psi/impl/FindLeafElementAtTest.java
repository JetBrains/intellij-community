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
package com.intellij.java.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightCodeInsightTestCase;

public class FindLeafElementAtTest extends LightCodeInsightTestCase {
  public void testFindLeafElementAtEof() {
    configureFromFileText("test.java", "class Foo {}");
    PsiFile file = getFile();
    ASTNode node = file.getNode().findLeafElementAt(file.getTextLength());
    assertNull(node);
  }
}