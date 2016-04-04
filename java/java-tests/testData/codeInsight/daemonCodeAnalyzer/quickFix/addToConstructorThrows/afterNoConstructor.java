// "Add exception to class default constructor signature" "true"
import java.io.FileWriter;
import java.io.IOException;

class C {
  final FileWriter fw = new FileWriter("asd");

    C() throws IOException {
    }
}
