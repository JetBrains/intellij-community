import java.util.*;

class InstanceofAndWhitespace {

  void foo(List<Object> os) {
    for<caret> (final Iterator<Object> iterator = os.iterator(); iterator.hasNext(); ) {
      if (iterator.next()instanceof String) {

      }
    }
  }
}