// "Make 'bar' return 'java.util.Iterator'" "true"
import java.util.ArrayList;
import java.util.Iterator;

public class Foo {
  Iterator bar() {
    return new ArrayList().iterator();
  }
}
