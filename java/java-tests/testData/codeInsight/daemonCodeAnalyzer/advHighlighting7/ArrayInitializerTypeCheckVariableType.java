import java.util.*;

class FooClass {

  {
    Set[][] a = null;
    a[0] = new Set[]{ <error descr="Incompatible types. Found: 'java.util.List', required: 'java.util.Set'">fooBar</error>(), <error descr="Incompatible types. Found: 'java.util.List', required: 'java.util.Set'">fooBar</error>()};
  }

  private List fooBar() {
    return null;
  }
}