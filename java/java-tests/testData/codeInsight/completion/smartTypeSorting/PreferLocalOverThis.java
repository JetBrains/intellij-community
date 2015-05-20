import java.lang.Object;
import java.util.List;

interface Intf {
  
}

public class Aaaaaaa implements Intf {
  void foo(List<Object> src, List<Intf> dst) {
    for (Object value : src) {
      if (value instanceof Intf) {
        dst.add(<caret>);
      }
    }
  }

}

