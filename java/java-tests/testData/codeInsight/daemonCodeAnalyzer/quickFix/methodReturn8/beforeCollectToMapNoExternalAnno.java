// "Make 'testMethodRef()' return 'java.util.Map<java.lang.String,java.lang.Integer>'" "true-preview"
import java.util.Map;
import java.util.stream.Collectors;

class Test {
  Map<String, String> testMethodRef(Map<String, Integer> list) {
    return <caret>list.entrySet().stream()
      .filter(e -> !e.getKey().isEmpty())
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}