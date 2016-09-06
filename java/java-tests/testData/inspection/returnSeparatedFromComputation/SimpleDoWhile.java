class T {
  String f(String a) {
    String r = "";
    int i = 0;
    do {
      int j = a.indexOf(",", i);
      String s = j > i ? a.substring(i, j) : a.substring(i);
      if (s.startsWith("@")) {
        r = s;
        break;
      }
      i = j + 1;
    }
    while (i >= 0);
    <warning descr="Return separated from computation of value of 'r'">return</warning> r;
  }

  boolean hasNext() {
    return true;
  }

  String next() {
    return null;
  }
}
