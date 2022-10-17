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

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

public class QuickFixActionRegistrarImpl implements QuickFixActionRegistrar {
  @NotNull
  private final HighlightInfo myInfo;

  public QuickFixActionRegistrarImpl(@NotNull HighlightInfo info) {
    myInfo = info;
  }

  @Override
  public void register(@NotNull IntentionAction action) {
    myInfo.registerFix(action, null, null, null, null);
  }

  @Override
  public void register(@NotNull TextRange fixRange, @NotNull IntentionAction action, HighlightDisplayKey key) {
    myInfo.registerFix(action, null, HighlightDisplayKey.getDisplayNameByKey(key), fixRange, key);
  }

  @Override
  public void unregister(@NotNull Condition<? super IntentionAction> condition) {
    myInfo.unregisterQuickFix(condition);
  }

  @Override
  public String toString() {
    return "QuickFixActionRegistrarImpl{" +
           "myInfo=" + myInfo +
           '}';
  }
}
