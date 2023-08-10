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

/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

public abstract class CharSequenceHashStubIndexExtension<Psi extends PsiElement> extends AbstractStubIndex<CharSequence, Psi> {

  @Override
  public int getVersion() {
    return 1;
  }

  @Override
  @NotNull
  public final KeyDescriptor<CharSequence> getKeyDescriptor() {
    return CharSequenceHashInlineKeyDescriptor.INSTANCE;
  }

  public boolean doesKeyMatchPsi(@NotNull CharSequence key, @NotNull Psi psi) {
    return true;
  }
}