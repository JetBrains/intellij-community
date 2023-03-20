// "Make 'a.f()' static" "true-preview"
import java.io.*;

class a {
  public static void f() {}
}
class b extends a {
  <caret>public static void f() {}
}
