// "Replace StringTokenizer delimiters parameter with unique symbols" "true"
import java.util.StringTokenizer;

class A {

  void m() {

    new StringTokenizer("asd", "\\\t\t\n<caret>\nqwerty")

  }

}