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
package com.intellij.ui;

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.psi.PsiElement;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * @author pegov
 * @deprecated use HintUpdateSupply directly
 * @see HintUpdateSupply
 */
@Deprecated
public abstract class JBListWithHintProvider extends JBList {
  {
    HintUpdateSupply.installHintUpdateSupply(this, o -> getPsiElementForHint(o));
  }

  public JBListWithHintProvider() {
  }

  public JBListWithHintProvider(ListModel dataModel) {
    super(dataModel);
  }

  public JBListWithHintProvider(Object... listData) {
    super(listData);
  }

  public JBListWithHintProvider(Collection items) {
    super(items);
  }
  
  @Nullable
  protected abstract PsiElement getPsiElementForHint(final Object selectedValue);
}
