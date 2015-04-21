/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement.std;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class CompositeArrangementToken extends StdArrangementSettingsToken {
  private final Set<ArrangementSettingsToken> myParentTokenTypes;
  
  private CompositeArrangementToken(@NotNull String id,
                                    @NotNull String uiName,
                                    @NotNull StdArrangementTokenType tokenType,
                                    @NotNull ArrangementSettingsToken... tokens)
  {
    super(id, uiName, tokenType);
    myParentTokenTypes = ContainerUtil.newHashSet(tokens);
  }

  @NotNull
  public static CompositeArrangementToken create(@NonNls @NotNull String id,
                                                 @NotNull StdArrangementTokenType tokenType,
                                                 @NotNull ArrangementSettingsToken... tokens)
  {
    return new CompositeArrangementToken(id, id.toLowerCase().replace("_", " "), tokenType, tokens);
  }
  
  @NotNull
  public Set<ArrangementSettingsToken> getAdditionalTokens() {
    return myParentTokenTypes;
  }

}
