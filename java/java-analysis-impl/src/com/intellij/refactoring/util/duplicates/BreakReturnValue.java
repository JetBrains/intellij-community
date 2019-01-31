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

package com.intellij.refactoring.util.duplicates;

import org.jetbrains.annotations.NonNls;

public class BreakReturnValue extends GotoReturnValue{
  @Override
  public boolean isEquivalent(final ReturnValue other) {
    return other instanceof BreakReturnValue;
  }

  @Override
  @NonNls
  public String getGotoStatement() {
    return "if(a) break;";
  }
}