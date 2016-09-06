class T {
  String f() {
    String r = "";
    while (hasNext()) {
      String s = next();
      if (s != null) {
        r = s;
        break;
      }
    }
    <warning descr="Return separated from computation of value of 'r'">return</warning> r;
  }

  boolean hasNext() {
    return true;
  }

  String next() {
    return null;
  }
}
