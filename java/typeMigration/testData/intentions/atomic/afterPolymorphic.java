// "Convert to atomic" "true"

import java.util.concurrent.atomic.AtomicReference;

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
    AtomicReference<Z> y = new AtomicReference<>(new Y());
    api(() -> y.set(new Y()));
  }

  public void api(Runnable runnable) {}
}