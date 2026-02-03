public class Super {
    void f<caret>oo() {
    }

}

class Child extends Super {
    void foo(){
      super.foo();
      System.out.println("do some staff");
    }
}

class ChildChild extends Child {
    void foo(){
      super.foo();
      System.out.println("here do smth else");
    }
}