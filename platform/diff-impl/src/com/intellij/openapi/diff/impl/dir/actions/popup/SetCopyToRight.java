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
package com.intellij.openapi.diff.impl.dir.actions.popup;

import com.intellij.ide.diff.DirDiffElement;
import com.intellij.ide.diff.DirDiffOperation;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class SetCopyToRight extends SetOperationToBase {
  @NotNull
  @Override
  protected DirDiffOperation getOperation() {
    return DirDiffOperation.COPY_TO;
  }

  @Override
  protected boolean isEnabledFor(DirDiffElement element) {
    return element.getSource() != null;
  }
}
