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

/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.stubs.PsiClassHolderFileStub;

public interface PsiJavaFileStub extends PsiClassHolderFileStub<PsiJavaFile> {
  PsiJavaModule getModule();

  String getPackageName();
  LanguageLevel getLanguageLevel();
  boolean isCompiled();

  StubPsiFactory getPsiFactory();

  /** @deprecated override {@link #getPsiFactory()} instead (to be removed in IDEA 18) */
  void setPsiFactory(StubPsiFactory factory);
}