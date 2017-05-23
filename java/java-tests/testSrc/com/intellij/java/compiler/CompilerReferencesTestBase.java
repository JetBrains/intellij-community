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
package com.intellij.java.compiler;

import com.intellij.compiler.CompilerReferenceService;
import com.intellij.java.codeInsight.completion.AbstractCompilerAwareTest;

public abstract class CompilerReferencesTestBase extends AbstractCompilerAwareTest {
  private boolean myDefaultEnableState;

  @Override
  public void setUp() throws Exception {
    myDefaultEnableState = CompilerReferenceService.IS_ENABLED_KEY.asBoolean();
    CompilerReferenceService.IS_ENABLED_KEY.setValue(true);
    super.setUp();
  }

  @Override
  public void tearDown() throws Exception {
    CompilerReferenceService.IS_ENABLED_KEY.setValue(myDefaultEnableState);
    super.tearDown();
  }
}
