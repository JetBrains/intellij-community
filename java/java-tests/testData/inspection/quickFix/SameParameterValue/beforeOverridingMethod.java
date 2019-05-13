// "f false" "true"
public class Test {
  void foo(boolean <caret>f){
    if (f) {
      System.out.println(f);
    }
  }
  void bar(){foo(false);}
}

class Test1 extends Test {
  @Override
  void foo(boolean f) {
    System.out.println(f);
  }
}