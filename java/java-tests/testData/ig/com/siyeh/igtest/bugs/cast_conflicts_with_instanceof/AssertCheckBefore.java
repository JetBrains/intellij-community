class AssertCheckBefore {
  void m(Object child, Object parent) {
    if (parent instanceof Number) {
      if (child instanceof String) {
        assert parent instanceof Integer;
        Integer attribute = (Integer) parent;
      }
    }
  }
}