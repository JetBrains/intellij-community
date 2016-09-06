class T {
  String f(String[] a) {
    String r = "";
    for (String s : a) {
      if (s != null && s.contains("@")) {
        r = s + ":" + s.length();
        break;
      }
    }
    <warning descr="Return separated from computation of value of 'r'">return</warning> r;
  }
}