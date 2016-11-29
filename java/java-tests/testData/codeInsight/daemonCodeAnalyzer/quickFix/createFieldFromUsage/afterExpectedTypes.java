// "Create field 'panel'" "true"
import java.io.*;

class Test {
    private File panel;

    Object foo(File container) {
    if (panel == null) {
      panel = new File();
      return new File(container, panel.getName());
    }
  }
}