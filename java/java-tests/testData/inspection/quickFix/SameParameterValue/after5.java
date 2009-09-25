// "f 5" "true"
public class Test {
  void foo(<caret>){
    if (5 == 5) {
      Syste.out.print(5);
    }
  }
  void bar(){foo();}
  void bar1(){foo();}
}