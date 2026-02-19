// "Remove duplication from 'delimiters' argument" "false"
import java.util.StringTokenizer;

class A {

  void m() {

    new StringTokenizer("asd", "\\\t\nqw<caret>erty!#2@$")

  }

}