// "Wrap using 'Paths.get()'" "true"
// "Wrap using 'Path.of()'" "false"
import java.nio.file.*;

class Test {

  Path m() {
    return "/<caret>etc/passwd";
  }

}