import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

class LocalVariableNamingConvention {

  void x(List<String> list) {
    try (FileInputStream _ = null) {} catch (IOException _) {
    }
    String _ = "a";
    int _ = 1, _ = 2;
    for (var _ : list) {}
  }
  
  void y() {
    String <warning descr="Local variable name 'a' is too short (1 < 3)">a</warning>;
    String correct;
  }
}