/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.autodetect;

import com.intellij.JavaTestUtil;
import org.jetbrains.annotations.NotNull;

public class JavaAutoDetectIndentTest extends AbstractIndentAutoDetectionTest {

  @NotNull
  @Override
  protected String getFileNameWithExtension() {
    return getTestName(true) + ".java";
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() +
           "/psi/autodetect/";
  }

  public void testNotIndentedComment() {
    doTestIndentSize(3);
  }

  public void testContinuationIndents_DoNotCount() {
    doTestIndentSize(2);
  }

  public void testContinuationIndent_JsonLiteral() {
    doTestIndentSize(4);
  }

  public void testContinuationIndents_InMethodParameters_DoNotCount() {
    doTestIndentSize(4);
  }

}
