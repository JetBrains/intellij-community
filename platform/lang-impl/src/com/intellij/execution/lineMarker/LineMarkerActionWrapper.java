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
package com.intellij.execution.lineMarker;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class LineMarkerActionWrapper extends AnAction {

  protected final PsiElement myElement;
  private final AnAction myOrigin;

  public LineMarkerActionWrapper(PsiElement element, @NotNull AnAction origin) {
    myElement = element;
    myOrigin = origin;
    copyFrom(origin);
  }

  @Override
  public void update(AnActionEvent e) {
    AnActionEvent event = wrapEvent(e);
    myOrigin.update(event);
  }

  @NotNull
  protected AnActionEvent wrapEvent(AnActionEvent e) {
    return new AnActionEvent(
      e.getInputEvent(), new MyDataContext(e.getDataContext()), e.getPlace(), e.getPresentation(), e.getActionManager(),
      e.getModifiers());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    myOrigin.actionPerformed(wrapEvent(e));
  }

  private class MyDataContext extends UserDataHolderBase implements DataContext {
    private final DataContext myDelegate;

    public MyDataContext(DataContext delegate) {
      myDelegate = delegate;
    }

    @Nullable
    @Override
    public synchronized Object getData(@NonNls String dataId) {
      if (Location.DATA_KEY.is(dataId)) return myElement.isValid() ? new PsiLocation<PsiElement>(myElement) : null;
      return myDelegate.getData(dataId);
    }
  }
}
