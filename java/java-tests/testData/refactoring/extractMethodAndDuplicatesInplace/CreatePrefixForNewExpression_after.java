import org.jetbrains.annotations.NotNull;

class X {
  void test() {
      List<String> list = createList();
      System.out.println(list);
  }

    private static @NotNull List<String> createList() {
        List<String> list = new ArrayList<>();
        list.add("x");
        list.add("y");
        return list;
    }
}