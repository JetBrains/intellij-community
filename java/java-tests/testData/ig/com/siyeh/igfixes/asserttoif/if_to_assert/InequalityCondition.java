class InequalityCondition {
  void m(Object o) {
    <caret>if (o != null) {
      throw new Throwable("wtf?");
    }
  }
}