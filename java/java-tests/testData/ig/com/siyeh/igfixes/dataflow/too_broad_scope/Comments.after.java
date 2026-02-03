import java.io.*;

public class Comments {
  void a() {
      try (PrintStream out = System.out) {
          // b
          /*some comment inside old declaration*/
          // a
          int s = out.hashCode(/*inside initializer*/);
          out.println(s);
    }
  }
}