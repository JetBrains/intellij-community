class C {
  String[] array;

  boolean test(String a, String b) {
    for (String s : array) {
      if (a.equals(s)) {
        <selection>
        if (b == null) {
          return true;
        }
        if (b.equals(s)) {
          return true;
        }
        </selection>
      }
    }
    return false;
  }
}