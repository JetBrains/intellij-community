class Test extends X {
  @Override
  boolean x() {
    return false;
  }
  boolean y() {
    return true;
  }
}

abstract class X {
  abstract boolean x();
}

class Y extends X {
  @Override
  boolean x() {
    return true;
  }
}