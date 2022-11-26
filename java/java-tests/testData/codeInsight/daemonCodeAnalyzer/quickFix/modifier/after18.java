// "Make 'a.f()' not final" "true-preview"
import java.io.*;

class a {
  public void f() {
  }
}
class b extends a {
  <caret>public void f() {}
}