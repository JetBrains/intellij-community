// LocalsOrMyInstanceFieldsControlFlowPolicy

public class a {
  boolean c;
  void f(int i) throws Exception {<caret>
        do {
            if (c) break;
            c=!c;
            if (c) continue;
        } while (!c);
  }
}