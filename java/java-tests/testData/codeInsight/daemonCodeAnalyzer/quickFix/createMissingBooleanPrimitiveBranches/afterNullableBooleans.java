// "Create missing switch branches with null branch" "true-preview"
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Test {
  public static void main(String[] args) {
    test(true);
  }

  public void test(@Nullable Boolean b) {
    switch (b) {
        case true -> {
        }
        case false -> {
        }
        case null -> {
        }
    }
  }
}