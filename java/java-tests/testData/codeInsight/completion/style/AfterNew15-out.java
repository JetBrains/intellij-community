import java.io.File;
import java.io.FilenameFilter;

class A {
  {
    new java.io.File("aaa").list(new FilenameFilter() {
        public boolean accept(File file, String s) {
            return false;
        }
    });
  }
}