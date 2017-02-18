
interface ITest {
  void act<caret>ion(Object o);
}


class Test implements ITest{

  public void action(Object o) {
  }

  private class ActionHandler {
    public void handleAction(Object o, Object o1) {
      action(o);
    }
  }
}