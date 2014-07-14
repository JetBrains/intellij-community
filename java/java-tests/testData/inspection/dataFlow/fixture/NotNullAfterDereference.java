class Foo {

  private void someMethod(String someArg) {
    someArg.getClass();
    if (<warning descr="Condition 'someArg == null' is always 'false'">someArg == null</warning>) {
      System.err.println("Wrong argument");
    }
  }
}