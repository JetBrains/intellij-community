class ArrayEquality {

  boolean a(String[] a, String[] b) {
    return a <warning descr="Array objects are compared using 'Arrays.equals()', not '=='">==</warning> b;
  }

  boolean b(Number[] n) {
    return n == null;
  }
}
