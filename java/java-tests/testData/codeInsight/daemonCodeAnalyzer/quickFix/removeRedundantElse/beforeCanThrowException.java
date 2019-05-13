// "Remove redundant 'else'" "false"
import java.io.IOException;
class a {
  void foo(boolean condition) throws IOException{
    if (condition) {
      tMethod();
    }
    e<caret>lse {
      System.out.println("else");
    }
  }

  void tMethod() throws IOException {}
}

