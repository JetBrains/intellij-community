// "Add exception to class default constructor signature" "false"
import java.io.FileWriter;

class C {
  final FileWriter fw = ((MyCall<caret>able) () -> new FileWriter("")).get();

  interface MyCallable {
    FileWriter get();
  }
}