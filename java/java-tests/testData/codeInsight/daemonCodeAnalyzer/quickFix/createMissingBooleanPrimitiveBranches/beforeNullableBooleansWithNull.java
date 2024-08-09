// "Create missing switch branches with null branch" "false"
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Test {
  public static void main(String[] args) {
    test(true);
  }

  public void test(@Nullable Boolean b) {
    switch (b<caret>) {
      case null -> System.out.println();
    }
  }
}