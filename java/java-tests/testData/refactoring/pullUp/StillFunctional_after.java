@FunctionalInterface
interface I {
  void foo();

    default void get() {}
}

class IImpl implements I {
  public void foo(){}
}