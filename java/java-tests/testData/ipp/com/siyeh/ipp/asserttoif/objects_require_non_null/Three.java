package objects_require_non_null;

class Three {
  void a(Integer i) {
    assert ((i) != (null));//comment
    System.out.println(i<caret>);
  }
}