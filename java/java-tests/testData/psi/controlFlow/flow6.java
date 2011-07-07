// LocalsOrMyInstanceFieldsControlFlowPolicy

public class a {
  boolean c;
  void f(int i) throws Exception {<caret>
        for (int i=0; i<100; i++) {
            if (i==0) break;
            if (i==1) continue;
            c = !c;
        }

  }
}