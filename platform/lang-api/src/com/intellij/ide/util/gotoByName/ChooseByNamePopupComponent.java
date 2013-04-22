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
package com.intellij.ide.util.gotoByName;

import com.intellij.openapi.application.ModalityState;

import java.util.List;

/**
 * User: anna
 * Date: Jan 26, 2005
 */
public interface ChooseByNamePopupComponent {
  void invoke(Callback callback, ModalityState modalityState, boolean allowMultipleSelection);

  Object getChosenElement();

  abstract class Callback {
    public abstract void elementChosen(Object element);
    public void onClose() { }
  }

  /**
   * Used for popups with multi selection enabled to pass all chosen elements at one time.
   *
   * @author Konstantin Bulenkov
   */
  abstract class MultiElementsCallback extends Callback {
    @Override
    public final void elementChosen(Object element){}
    public abstract void elementsChosen(List<Object> elements);
  }
}
