// "Add exception to class default constructor signature" "true"
import java.io.FileWriter;

class C {
  final FileWriter fw = new FileWrit<caret>er("asd");

}
