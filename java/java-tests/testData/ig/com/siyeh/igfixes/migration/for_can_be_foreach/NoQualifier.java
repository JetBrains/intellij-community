import java.util.*;

class NoQualifier {
  List<String> values = new ArrayList<>();
  void foo() {
    <caret>for (int i = 0; i < values.size(); i++) {
      String value = this.values.get(i);
    }
  }
}