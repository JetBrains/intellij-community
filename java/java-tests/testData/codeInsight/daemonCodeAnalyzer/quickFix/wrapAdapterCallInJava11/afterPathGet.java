// "Wrap parameter using 'Paths.get()'" "true"
import java.nio.file.*;

class Test {

  void m() {
    Files.readString(Paths.get("path"));
  }

}