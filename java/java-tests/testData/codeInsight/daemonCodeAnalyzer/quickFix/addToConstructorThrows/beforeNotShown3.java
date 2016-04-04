// "Add exception to class constructor signature" "false"
import java.io.FileWriter;
import java.io.IOException;

class C {
  final FileWriter fw = new F<caret>ileWriter("asd");

  C() throws IOException {

  }
}