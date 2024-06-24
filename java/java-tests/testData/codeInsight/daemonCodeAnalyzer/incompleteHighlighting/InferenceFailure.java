import java.util.*;
import <info descr="Not resolved until the project is fully loaded">foo</info>.<info descr="Not resolved until the project is fully loaded">bar</info>.<info descr="Not resolved until the project is fully loaded">baz</info>.*;

class Test {
  void testDiamond() {
    List<<info descr="Not resolved until the project is fully loaded">X</info>> list = new ArrayList<>();
  }
  
  void test() {
    List<String> data = new ArrayList<>(
      Arrays.asList(<info descr="Not resolved until the project is fully loaded">a</info>.<info descr="Not resolved until the project is fully loaded">X</info>, <info descr="Not resolved until the project is fully loaded">a</info>.<info descr="Not resolved until the project is fully loaded">Y</info>));
  }
}