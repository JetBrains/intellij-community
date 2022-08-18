// "Make 'inner class initializer' not static" "true-preview"
import java.io.*;

class a {
  class inner {
    <caret>static {
    }
  }
}
