// "Make 'inner class initializer' not static" "true"
import java.io.*;

class a {
  class inner {
    <caret>static {
    }
  }
}
