class Test {
  void h(int i, String[] s, String[] t) {
      final String s1 = newMethod(t[i], s[t[i].length()]);
      System.out.println(s1);
  }

    private String newMethod(String x, String s11) {
        System.out.println(x);
        return s11;
    }
}