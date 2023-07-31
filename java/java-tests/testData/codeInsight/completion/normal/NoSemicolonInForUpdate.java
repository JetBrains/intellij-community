import java.util.*;

class SomeTest {
  void a() {
    Collection<?> c = Collections.emptyList();

    for(Iterator<?> i=c.iterator(); i.hasNext();i.rem<caret>) {

    }
  }
}