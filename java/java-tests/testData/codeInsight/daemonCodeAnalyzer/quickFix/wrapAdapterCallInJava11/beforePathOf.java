// "Wrap parameter using 'Path.of()'" "true"
import java.nio.file.*;

class Test {

  void m() {
    Files.readString("<caret>path");
  }

}