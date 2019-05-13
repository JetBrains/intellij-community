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
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.InsertHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author peter
 */
public abstract class MutableLookupElement<T> extends LookupElement{
  @Override
  @NotNull
  public abstract T getObject();

  public abstract MutableLookupElement<T> setBold();

  public abstract MutableLookupElement<T> setAutoCompletionPolicy(AutoCompletionPolicy policy);

  @NotNull
  public abstract MutableLookupElement<T> setIcon(Icon icon);

  @NotNull
  public abstract MutableLookupElement<T> setPriority(double priority);

  @NotNull
  public abstract MutableLookupElement<T> setPresentableText(@NotNull String displayText);

  @NotNull
  public abstract MutableLookupElement<T> setTailType(@NotNull TailType type);

  public abstract MutableLookupElement<T> addLookupStrings(@NonNls String... additionalLookupStrings);

  public abstract MutableLookupElement<T> setInsertHandler(InsertHandler<? extends LookupElement> insertHandler);

  public abstract InsertHandler<? extends MutableLookupElement> getInsertHandler();

  public abstract boolean isBold();
}
