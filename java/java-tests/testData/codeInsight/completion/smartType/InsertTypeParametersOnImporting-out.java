import java.util.HashMap;
import java.util.Map;

class Foo {
  {
    Map<String,String> m = new HashMap<String, String>(<caret>);
  }
}