import java.util.List;

class Test {
  void foo(String[] a, List<String> b) {
    for (int i = 0; i < a.length; i++) {
        newMethod(a[i]);
    }

    for (int i = 0; i < b.size(); i++) {
        newMethod(b.get(i));
    }
  }

    private void newMethod(String s) {
        System.out.println(s);
    }
}