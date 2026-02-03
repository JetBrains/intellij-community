// LocalsOrMyInstanceFieldsControlFlowPolicy
public class a {
  void f(int i) {<caret>
    while (i==0) {
      i = 5;
      if (i==3) break;
    }
  }
}