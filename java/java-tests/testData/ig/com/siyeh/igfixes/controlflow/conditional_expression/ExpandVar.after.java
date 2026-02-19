import java.util.*;

class Foo {
  void m(boolean b){
      List<Integer> p<caret>;
      if (b) p = Arrays.asList(1);
      else p = Arrays.asList(2);
  }
}