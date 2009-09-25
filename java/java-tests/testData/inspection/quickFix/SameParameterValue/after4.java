// "f false" "true"
public class Test {
  void foo(<caret>boolean b){
    if (false) {
      Syste.out.print(false);
    }
  }
  void bar(){foo(false);}
  void bar1(){foo(true);}
}