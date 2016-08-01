import java.util.HashMap;
import java.util.Map;

final class Foo {
  private Map<String, Object> map = new HashMap<>();

  private Map<String, Object> getMap() {
    return m<caret>ap;
  }
}