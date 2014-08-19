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
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ObjectUtils;

import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

/**
 * @deprecated
 * @see com.intellij.ui.popup.HintUpdateSupply
 */
public abstract class JBTableWithHintProvider extends JBTable {

  {
    new HintUpdateSupply(this) {
      @Override
      protected PsiElement getPsiElementForHint(Object selectedValue) {
        return JBTableWithHintProvider.this.getPsiElementForHint(selectedValue);
      }
    };
  }

  public JBTableWithHintProvider() {
  }

  protected JBTableWithHintProvider(TableModel model) {
    super(model);
  }

  public JBTableWithHintProvider(TableModel model, TableColumnModel columnModel) {
    super(model, columnModel);
  }

  protected abstract PsiElement getPsiElementForHint(final Object selectedValue);

  @Deprecated
  public void registerHint(JBPopup hint) {
    ObjectUtils.assertNotNull(HintUpdateSupply.getSupply(this)).registerHint(hint);
  }

  @Deprecated
  public void hideHint() {
    ObjectUtils.assertNotNull(HintUpdateSupply.getSupply(this)).hideHint();
  }

  @Deprecated
  public void updateHint(PsiElement element) {
    ObjectUtils.assertNotNull(HintUpdateSupply.getSupply(this)).updateHint(element);
  }
}
