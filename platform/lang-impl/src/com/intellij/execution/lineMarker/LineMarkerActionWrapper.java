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
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class LineMarkerActionWrapper extends AnAction {
  public static final Key<Pair<PsiElement, MyDataContext>> LOCATION_WRAPPER = Key.create("LOCATION_WRAPPER");

  protected final PsiElement myElement;
  private final AnAction myOrigin;

  public LineMarkerActionWrapper(PsiElement element, @NotNull AnAction origin) {
    myElement = element;
    myOrigin = origin;
    copyFrom(origin);
  }

  @Override
  public void update(AnActionEvent e) {
    myOrigin.update(wrapEvent(e));
  }

  @NotNull
  private AnActionEvent wrapEvent(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Pair<PsiElement, MyDataContext> pair = DataManager.getInstance().loadFromDataContext(dataContext, LOCATION_WRAPPER);
    if (pair == null || pair.first != myElement) {
      pair = Pair.pair(myElement, new MyDataContext(dataContext));
      DataManager.getInstance().saveInDataContext(dataContext, LOCATION_WRAPPER, pair);
    }
    return new AnActionEvent(e.getInputEvent(), pair.second, e.getPlace(), e.getPresentation(), e.getActionManager(), e.getModifiers());
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
      if (Location.DATA_KEY.is(dataId)) return myElement.isValid() ? new PsiLocation<>(myElement) : null;
      return myDelegate.getData(dataId);
    }
  }
}
