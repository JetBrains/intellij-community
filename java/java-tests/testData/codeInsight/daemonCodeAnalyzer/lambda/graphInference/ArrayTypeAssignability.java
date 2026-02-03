import java.io.Serializable;
import java.util.List;

class Test {
  public void serialize(Serializable o) {}

  public void foo(List<String> strings) {
    serialize(strings.toArray(new String[0]));
  }
}