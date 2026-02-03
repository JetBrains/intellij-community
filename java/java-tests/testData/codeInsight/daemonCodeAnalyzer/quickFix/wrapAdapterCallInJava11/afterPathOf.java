// "Adapt argument using 'Path.of()'" "true-preview"
import java.nio.file.*;

class Test {

  void m() {
    Files.readString(Path.of("path"));
  }

}