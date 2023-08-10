import java.util.List;

class PrimitiveItem {
  void foo(List<? extends Object> it) {
      <caret>for (int j = 0, itSize = it.size(); j < itSize; j++) {
          var i = it.get(j);
          System.out.println(i);
      }
  }
}