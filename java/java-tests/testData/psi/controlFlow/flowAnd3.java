// LocalsOrMyInstanceFieldsControlFlowPolicy

class c {
  String container;
  void f() {<caret>
    String parent;
    if (container == null && (parent = null) != null && parent != null) {}
  }
}