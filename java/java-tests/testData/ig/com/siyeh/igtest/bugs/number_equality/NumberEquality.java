class NumberEquality {
  static final Integer CONSTANT = new Integer(10);

  boolean f(Integer i, Integer j) {
    return i <warning descr="Number objects are compared using '==', not 'equals()'">==</warning> j;
  }
  
  void test(Integer i) {
    // Probably a questionable code style to use a number as a sentinel value
    // (depending on the constant exact type)
    // but it should be reported at constant initialization, not at comparison
    if (i == CONSTANT) {}
  }

  boolean g(Integer i) {
    return i == null;
  }
}