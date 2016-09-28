interface Test {
  void foo();
}

class B implements Test {
    @Override
    public void foo() {
      System.out.println();
    }
}