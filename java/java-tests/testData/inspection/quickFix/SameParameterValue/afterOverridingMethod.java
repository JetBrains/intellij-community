// "f false" "true"
public class Test {
  void foo(){
    if (false) {
      System.out.println(false);
    }
  }
  void bar(){foo();}
}

class Test1 extends Test {
  @Override
  void foo() {
    System.out.println(false);
  }
}