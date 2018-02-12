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
package com.intellij.psi.codeStyle.arrangement.std;

import com.intellij.openapi.application.ApplicationBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/**
 * Represents ArrangementSettingsToken designed for use with standard GUI, i.e. a token that knows its UI role.
 * @author Roman.Shein
 */
public class StdArrangementSettingsToken extends ArrangementSettingsToken {

  @NotNull private final StdArrangementTokenType myTokenType;

  @NotNull
  public static StdArrangementSettingsToken tokenById(@NotNull String id,
                                                      @NotNull StdArrangementTokenType tokenType) {
    return new StdArrangementSettingsToken(id, id.toLowerCase().replace("_", " "), tokenType);
  }

  @NotNull
  public static StdArrangementSettingsToken token(@NotNull String id,
                                                  @NotNull String name,
                                                  @NotNull StdArrangementTokenType tokenType) {
    return new StdArrangementSettingsToken(id, name, tokenType);
  }

  @NotNull
  public static StdArrangementSettingsToken tokenByBundle(@NotNull String id,
                                                          @NotNull @PropertyKey(resourceBundle = ApplicationBundle.BUNDLE) String key,
                                                          @NotNull StdArrangementTokenType tokenType) {
    return new StdArrangementSettingsToken(id, ApplicationBundle.message(key), tokenType);
  }

  @NotNull public StdArrangementTokenType getTokenType() {
    return myTokenType;
  }

  protected StdArrangementSettingsToken(@NotNull String id,
                                        @NotNull String uiName,
                                        @NotNull StdArrangementTokenType tokenType) {
    super(id, uiName);
    myTokenType = tokenType;
  }
}
