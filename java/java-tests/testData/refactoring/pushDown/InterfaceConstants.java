interface SimpleDialogPresenter {
  int BUT<caret>TON_POSITIVE = 1;
  int BUTTON_NEGATIVE = 2;
  int BUTTON_NEUTRAL = 3;
}

class SimpleDialogPresenterImpl implements SimpleDialogPresenter {}