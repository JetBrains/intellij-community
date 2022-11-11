// "Adapt using 'Paths.get()'" "true-preview"
import java.nio.file.*;

class Test {

  Path m() {
    return Paths.get("/etc/passwd");
  }

}