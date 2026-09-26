import org.jetbrains.annotations.*;

class Test {
  void test(int x) {
    Integer y = null;
    switch (x) {
      case <error descr="Constant expression required">y</error>:
    }
  }
}