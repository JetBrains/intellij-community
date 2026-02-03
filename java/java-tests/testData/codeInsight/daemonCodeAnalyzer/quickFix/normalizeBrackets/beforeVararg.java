// "Apply all 'Replace with Java-style array declaration' fixes in file" "true"
import java.util.List;
class Vararg {

  void x(int... ns[][]<caret>) {}
  void vararg(List<String>... values[]) {}
}