// "f "\n"" "true"
public class Test {
  void foo(String <caret>f){
    System.out.println(f);
  }
  void bar(){foo("\n");}
  void bar1(){foo("\n");}
}