// "Remove redundant Optional chain" "true"
import java.util.Optional;

class Test {
  void execute(String s) {
    System.out.println(Optional./*1*/<caret>ofNullable(/*2*/someFunc(/*3*/)/*4*/).orElse(/*5*/null)/*6*/);
  }

  String someFunc() {throw new IllegalStateException();}
}