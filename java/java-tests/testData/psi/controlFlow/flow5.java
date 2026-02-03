// LocalsOrMyInstanceFieldsControlFlowPolicy

public class a {
  void f(int i) throws Exception {<caret>
        int k =0;
        switch (k) {
            case 0: k=0; break;
            case 1: k=1; break;
            default: k=9; break;
        }

  }
}