// "Make 'Anonymous class derived from a class initializer' not static" "true-preview"
import java.io.*;

class a {
  void f() {
    new a() {
      <caret>{
      }
    };
  }
}
