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
package com.intellij.ui.dualView;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.ColumnInfo;

/**
 * author: lesya
 */
public abstract class DualViewColumnInfo<Item, Aspect> extends ColumnInfo<Item, Aspect>{
  public DualViewColumnInfo(@NlsContexts.ColumnName String name) {
    super(name);
  }

  public abstract boolean shouldBeShownIsTheTree();

  public abstract boolean shouldBeShownIsTheTable();
}
