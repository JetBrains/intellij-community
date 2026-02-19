import java.util.*;

class Test {

  void test(int last)
  {
    for (; <error descr="Cannot resolve symbol 'i'">i</error> < last; <error descr="Cannot resolve symbol 'i'">i</error>++) {
    }
  }
}