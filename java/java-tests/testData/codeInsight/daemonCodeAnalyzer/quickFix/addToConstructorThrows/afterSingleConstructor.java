// "Add exception to class constructor signature" "true"
import java.io.FileWriter;
import java.io.IOException;

class C {
  final FileWriter fw = new FileWriter("asd");

  C(int i) throws RuntimeException, IOException {

  }
}
