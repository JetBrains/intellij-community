// LocalsOrMyInstanceFieldsControlFlowPolicy
public class a {
  void f() {<caret>
    int i = 0;
    if (i==0) {
      i = 5;
      i = 9;
    }
  }
}