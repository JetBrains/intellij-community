import java.io.File;
import java.io.FilenameFilter;

class A {
  {
    new java.io.File("aaa").list(new FilenameFilter() {
        public boolean accept(File dir, String name) {
            <selection>return false;</selection>
        }
    });
  }
}