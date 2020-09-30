interface Funct<caret>ional {
  void run();
}

class Inheritor implements Functional {
  @Override
  public void run() {

  }
}

class A {
  void foo() {
    Functional f = new Functional() {
      @Override
      public void run() {

      }
    };
  }
}