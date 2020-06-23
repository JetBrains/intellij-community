class Test {
  void h(int i, String[] s, String[] t) {
      final String s1 = newMethod(t[i], s[t[i].length()]);
      System.out.println(s1);
  }

    private String newMethod(String s2, String s11) {
        System.out.println(s2);
        return s11;
    }
}