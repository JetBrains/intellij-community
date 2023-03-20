// "Adapt argument using 'Paths.get()'" "false"
import java.nio.file.*;

class Test {

  void m() {
    Files.readString("<caret>path");
  }

}