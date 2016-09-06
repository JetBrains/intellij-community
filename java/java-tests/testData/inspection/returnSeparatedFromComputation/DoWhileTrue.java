class T {
  String f() {
    String r = "";
    do {
      if (!hasNext()) break;
      String s = next();
      if (s != null) {
        r = s;
        break;
      }
    } while (true);
    <warning descr="Return separated from computation of value of 'r'">return</warning> r;
  }

  boolean hasNext() {
    return true;
  }

  String next() {
    return null;
  }
}
