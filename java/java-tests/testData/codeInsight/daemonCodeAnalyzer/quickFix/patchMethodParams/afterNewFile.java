// "Adapt argument using 'new File()'" "true-preview"
import java.util.*;
import java.io.File;

class Demo {
  void test(int value) {
    Set<File> file = Set.of(new File("/etc/passwd"));
  }
}
