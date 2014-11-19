class Test {
  interface I {
    String foo();
  }
  public void foo(int ii) {
    I r = () -> {
        return newMethod();
    };
  }

    private String newMethod() {
        return "42";
    }
}
