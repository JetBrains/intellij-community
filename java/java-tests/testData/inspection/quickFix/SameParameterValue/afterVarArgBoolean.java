// "f true" "true"
public class Test {
  void foo(){
    if (new boolean[]{true}[0]) {
      System.out.println("hello");
    } else {
      System.out.println("goodbye");
    }
  }
  void bar(){foo();}
  void bar1(){foo();}
}