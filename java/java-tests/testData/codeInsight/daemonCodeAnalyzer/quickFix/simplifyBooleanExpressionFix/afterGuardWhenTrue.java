// "Remove guard expression" "true-preview"
class X {
  private static void test(Object o) {
    switch (o) {
      case String t -> System.out.println(1);
      case Object o1 -> System.out.println(1);
    }
  }
}