// "Make 'ininner' not static" "true-preview"
import java.io.*;

class a {
  class inner {
    <caret>static class ininner {
    }
  }
}
