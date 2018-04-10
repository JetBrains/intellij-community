// "f "abcd"" "true"
public class Test {
  void foo(String <caret>f){
    Syste.out.print(f);
  }
  void bar1(){foo("abcd");}
}