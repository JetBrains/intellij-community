package com.intellij.ide.util.gotoByName;

import com.intellij.openapi.application.ModalityState;

/**
 * User: anna
 * Date: Jan 26, 2005
 */
public interface ChooseByNamePopupComponent {
  void invoke(Callback callback, ModalityState modalityState, boolean allowMultipleSelection);

  Object getChosenElement();

  static abstract class Callback {
    public abstract void elementChosen(Object element);
    public void onClose() { }
  }
}
