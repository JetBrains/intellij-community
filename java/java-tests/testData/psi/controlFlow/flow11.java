// LocalsOrMyInstanceFieldsControlFlowPolicy


public class a {
  int f(boolean b1, boolean b2) {<caret>
    while (b1 || b2) {
    }
    return 0;
  }

}
