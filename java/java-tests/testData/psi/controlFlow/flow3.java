// LocalsOrMyInstanceFieldsControlFlowPolicy

public class a {
  void f(int i) throws Exception {<caret>
      i = 5;
      try {
        f(i);
      } catch (Exception e) {
        i = 0;
      }
      finally {
        i = 9;
      }


  }
}