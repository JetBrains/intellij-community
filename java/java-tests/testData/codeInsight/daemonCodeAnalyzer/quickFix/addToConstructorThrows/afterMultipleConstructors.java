// "Add exception to class constructors signature" "true"
import java.io.FileWriter;
import java.io.IOException;

class C {
  final FileWriter fw = new FileWriter("asd");

  C(int i) throws RuntimeException, IOException {

  }

  C(int i, int j) throws IOException {

  }

  C(int i, int j, int k) throws Exception {

  }

  C(int i, int j, int k, int l) throws IOException {

  }
}
