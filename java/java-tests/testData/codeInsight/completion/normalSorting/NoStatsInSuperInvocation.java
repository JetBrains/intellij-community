import java.lang.Object;
import java.lang.Override;
import java.util.HashMap;

class Foo extends HashMap {
  @Override
  public Object get(Object key) {
    pu<caret>
  }
}
