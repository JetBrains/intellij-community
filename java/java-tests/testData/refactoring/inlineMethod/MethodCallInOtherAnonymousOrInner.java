public class Parent {
  void execute(){}
  void foo<caret>execute(){
    execute();
  }
}

class Child extends Parent {
  void foo() {
    fooexecute();
    new Runnable() {
      public void run() {
        fooexecute();
      }
    }.run();
  }

  class InnerChild {
    void bar() {
      Child.this.fooexecute();
    }
  }
}