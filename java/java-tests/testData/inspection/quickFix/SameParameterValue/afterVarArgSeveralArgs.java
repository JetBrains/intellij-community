// "f true" "true"
public class Test {
  void foo(int x){
    if (new boolean[]{true}[0]) {
      System.out.println("hello");
    } else {
      System.out.println("goodbye");
    }
  }
  void bar(){foo(1);}
  void bar1(){foo(2);}
}