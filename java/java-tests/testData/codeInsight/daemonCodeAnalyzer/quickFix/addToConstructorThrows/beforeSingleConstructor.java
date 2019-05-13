// "Add exception to class constructor signature" "true"
import java.io.FileWriter;

class C {
  final FileWriter fw = new F<caret>ileWriter("asd");

  C(int i) throws RuntimeException {

  }
}
