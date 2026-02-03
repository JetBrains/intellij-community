// LocalsOrMyInstanceFieldsControlFlowPolicy
class TestForeach {
  {<caret>
    String[] args = new String[256];

    for(String s : args) {
      foo(s);
    }
  }
}