interface Test {
  void bar();
}

abstract class Child implements Test {
    @Override
    public abstract void bar();
}
