// "Wrap using 'Paths.get()'" "true"
import java.nio.file.*;

class Test {

  Path m() {
    return Paths.get("/etc/passwd");
  }

}