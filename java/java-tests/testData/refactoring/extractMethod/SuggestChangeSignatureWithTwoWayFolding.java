import java.util.List;

class Test {
  void foo(String[] a, List<String> b) {
    for (int i = 0; i < a.length; i++) {
      <selection>System.out.println(a[i]);</selection>
    }

    for (int i = 0; i < b.size(); i++) {
      System.out.println(b.get(i));
    }
  }
}