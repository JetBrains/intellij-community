public class Simple {
  void test(int x) {
    if (x >= 10) {
      if (x <weak_warning descr="Can be replaced with equality"><=</weak_warning> 10) {}
      if (10 <weak_warning descr="Can be replaced with equality">>=</weak_warning> x) {}
    }
  }

  void test2(String s, String[] arr) {
    if (s.length() <weak_warning descr="Can be replaced with equality"><=</weak_warning> 0) {}
    if (arr.length <weak_warning descr="Can be replaced with equality"><=</weak_warning> 0) {}
  }
}