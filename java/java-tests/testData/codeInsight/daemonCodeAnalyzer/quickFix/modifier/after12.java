// "Make 'ininner' not static" "true-preview"
import java.io.*;

class a {
  class inner {
    <caret>class ininner {
    }
  }
}
