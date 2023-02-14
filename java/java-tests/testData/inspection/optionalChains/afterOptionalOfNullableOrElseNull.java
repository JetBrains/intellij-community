// "Remove redundant Optional chain" "true"
import java.util.Optional;

class Test {
  void execute(String s) {
      /*1*/
      /*2*/
      /*4*/
      /*5*/
      System.out.println(someFunc(/*3*/)/*6*/);
  }

  String someFunc() {throw new IllegalStateException();}
}