import org.jetbrains.annotations.*;
import java.io.IOException;

class Foo {
  public void x() throws IOException {
     @Nullable String foo = "";
     while (foo.length() == 0) {
       foo = y();
       if (foo == null) throw new IOException("foo");
     }
  }
  
  public String y() {
     return "";
  }
}
