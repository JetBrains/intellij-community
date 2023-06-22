// "Convert to atomic" "true"

abstract class Z {
  abstract public Y getY();
}

class Y extends Z {
  @Override
  public Y getY() {
    return null;
  }
}

class Main {
  public void testSomething() {
    Z y = new Y();
    api(() -> y<caret> = new Y());
  }

  public void api(Runnable runnable) {}
}