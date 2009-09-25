// "f false" "true"
public class Test {
  void foo(<caret>){
    if (false) {
      Syste.out.print(false);
    }
  }
  void bar(){foo();}
  void bar1(){foo();}
}