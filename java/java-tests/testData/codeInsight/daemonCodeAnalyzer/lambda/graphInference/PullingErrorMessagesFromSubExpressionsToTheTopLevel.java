import java.util.*;

class MyTest {

  {
    Set<Integer> set = new TreeSet<>(Comparator.comparing(b -> ""));
    Set<Integer> set1 = new TreeSet<>(Comparator.comparing(b -> b != null ? "" : ""));
  }


  void f(List<String> l) {
    setCategories(l.stream().toArray(size -> new String[size]));
    setCategories(l.stream().toArray(String[]::new));
  }

  private void setCategories(String... strings) {}

}
