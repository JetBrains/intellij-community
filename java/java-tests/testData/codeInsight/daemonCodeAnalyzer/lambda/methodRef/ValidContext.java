import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

class Test {
  {
    if (<error descr="Method reference expression is not expected here">Test::length</error> instanceof String) {
    }
    bar(Test::length);
  }

  public static Integer length(String s) {
    return s.length();
  }
  
  public static void bar(Bar bar) {}
  
  interface Bar {
    Integer _(String s);
  }

  void f() throws IOException {
    try (BufferedReader reader = new BufferedReader(new FileReader(""))) {
      for (String line : <error descr="Method reference expression is not expected here">reader::lines</error>) {}
    }
  }
}