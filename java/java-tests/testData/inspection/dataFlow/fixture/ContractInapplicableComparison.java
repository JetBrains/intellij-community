import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

class Foo {
  void foo() {
    Object o1 = bar(goo());
    if (o1 == null) {
      System.out.println();
    }
  }

  @Nullable Object goo() { return null;}

  @Nullable @Contract("null->null") static Object bar(@Nullable Object foo) { return foo; }

}
