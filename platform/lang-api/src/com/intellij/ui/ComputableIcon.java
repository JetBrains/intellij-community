/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.meta.PsiPresentableMetaData;

import javax.swing.*;

public class ComputableIcon {

  private Icon myIcon;
  private Computable<Icon> myEvaluator;
  private boolean myEvaluated;

  public ComputableIcon(Computable<Icon> evaluator) {
    myEvaluator = evaluator;
  }

  public Icon getIcon() {
    if (!myEvaluated) {
      myIcon = myEvaluator.compute();
      myEvaluator = null;
      myEvaluated = true;
    }

    return myIcon;
  }

  public static ComputableIcon create(final Icon icon) {
    return new ComputableIcon(new Computable<Icon>() {
      @Override
      public Icon compute() {
        return icon;
      }
    });
  }

  public static ComputableIcon create(final ItemPresentation presentation, final boolean isOpen) {
    return new ComputableIcon(new Computable<Icon>() {
      @Override
      public Icon compute() {
        return presentation.getIcon(isOpen);
      }
    });
  }

  public static ComputableIcon create(final PsiPresentableMetaData data) {
    return new ComputableIcon(new Computable<Icon>() {
      @Override
      public Icon compute() {
        return data.getIcon();
      }
    });
  }

  public static ComputableIcon create(final VirtualFile file) {
    return new ComputableIcon(new Computable<Icon>() {
      @Override
      public Icon compute() {
        return file.getIcon();
      }
    });
  }
}
