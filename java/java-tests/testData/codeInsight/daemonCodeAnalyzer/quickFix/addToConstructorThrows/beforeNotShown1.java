// "Add exception to class default constructor signature" "false"
import java.io.FileWriter;

class C {
  final FileWriter fw = new MyCal<caret>lable() {
    @Override
    public FileWriter get() {
      return new FileWriter("");
    }
  }.get();

  interface MyCallable {
    FileWriter get();
  }
}