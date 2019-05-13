// "Add exception to class constructors signature" "true"
import java.io.FileWriter;
import java.io.IOException;

class C {
  final FileWriter fw = new FileWri<caret>ter("asd");

  C(int i) throws RuntimeException {

  }

  C(int i, int j) throws IOException {

  }

  C(int i, int j, int k) throws Exception {

  }

  C(int i, int j, int k, int l) {

  }
}
