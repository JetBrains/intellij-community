/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.util.gotoByName;

import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ChooseByNameModelEx extends ChooseByNameModel {
  void processNames(Processor<String> processor, boolean inLibraries);

  /**
   * @return the item provider to be used by ChooseByName components with this model. By default it's {@link DefaultChooseByNameItemProvider}.
   */
  @NotNull
  default ChooseByNameItemProvider getItemProvider(@Nullable PsiElement context) {
    return new DefaultChooseByNameItemProvider(context);
  }

  @NotNull
  static ChooseByNameItemProvider getItemProvider(@NotNull ChooseByNameModel model, @Nullable PsiElement context) {
    return model instanceof ChooseByNameModelEx ? ((ChooseByNameModelEx)model).getItemProvider(context)
                                                : new DefaultChooseByNameItemProvider(context);
  }
}
