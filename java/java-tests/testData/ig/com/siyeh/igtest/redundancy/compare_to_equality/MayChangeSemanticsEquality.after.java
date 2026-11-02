import org.jetbrains.annotations.Nullable;

enum Count {
  ONE, TWO, THREE;

  void test(@Nullable Count other) {
    if (this == o<caret>ther) {
      System.out.println("equal");
    }
  }
}