package p;
import java.util.Map;
class Foo {
  {
    Map<String, String> m = Bar.get();
  }
}
