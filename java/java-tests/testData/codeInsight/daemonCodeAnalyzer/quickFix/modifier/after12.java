// "Make 'ininner' not static" "true"
import java.io.*;

class a {
  class inner {
    <caret>class ininner {
    }
  }
}
