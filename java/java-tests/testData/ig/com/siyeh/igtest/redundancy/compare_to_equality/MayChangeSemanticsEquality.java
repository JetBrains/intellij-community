import org.jetbrains.annotations.Nullable;

enum Count {
  ONE, TWO, THREE;

  void test(@Nullable Count other) {
    if (compareTo<caret>(other) == 0) {
      System.out.println("equal");
    }
  }
}