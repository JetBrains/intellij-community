// LocalsOrMyInstanceFieldsControlFlowPolicy

public class a {
  boolean c;
  void f(int i) throws Exception {<caret>
        do {
            c=!c;
        } while (!c);

  }
}