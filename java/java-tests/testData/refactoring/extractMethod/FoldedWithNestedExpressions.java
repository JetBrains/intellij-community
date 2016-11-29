class Test {
  void h(int i, String[] s, String[] t) {
    <selection>System.out.println(t[i]);
    final String s1 = s[t[i].length()];</selection>
    System.out.println(s1);
  }
}