class PopupThirdItem {
  {
    setListener(new <caret>);
  }

  void setListener(Listener listener) {}
}

interface Listener {
  void methodA();
  void methodB();
  void methodC();
}

abstract class AbstractListener implements Listener {
  @Override
  public void methodA() {}

  @Override
  public void methodB() {}

  @Override
  public void methodC() {}
}