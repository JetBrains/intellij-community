// "Remove duplication from 'delimiters' argument" "true"
import java.util.StringTokenizer;

class A {

  void m() {

    new StringTokenizer("asd", "\"\\\t\t\n<caret>\nqwerty''\"")

  }

}