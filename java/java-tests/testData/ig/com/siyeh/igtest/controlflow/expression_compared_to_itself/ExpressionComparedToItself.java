class X {
  void test(String a, String b, int start) {
    if (Math.random() <warning descr="Expression is compared to itself">==</warning> Math.random()) {

    }
    if (a.substring(start, 10).length() + b.length() <warning descr="Expression is compared to itself">></warning> b.length() + a.substring(start, 0xA).length()) {

    }
    // Reported by 'Constant values'
    if (10 == 10) {}
    if (a.length() == a.length()) {}
  }
}