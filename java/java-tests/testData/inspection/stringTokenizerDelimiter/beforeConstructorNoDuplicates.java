// "Replace StringTokenizer delimiters parameter with unique symbols" "false"
import java.util.StringTokenizer;

class A {

  void m() {

    new StringTokenizer("asd", "\\\t\nqw<caret>erty!#2@$")

  }

}