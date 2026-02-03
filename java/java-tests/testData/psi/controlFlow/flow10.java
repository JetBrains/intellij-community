// LocalsOrMyInstanceFieldsControlFlowPolicy


public class d {
  void f(d[] aspects) {
    for (int i = 0; i < aspects.length; i++) {<caret>
      final int line;
      try {
        line = Integer.parseInt("2");  // /
      }
      catch (Exception e) {
        continue;
      }
    }
  }

}
