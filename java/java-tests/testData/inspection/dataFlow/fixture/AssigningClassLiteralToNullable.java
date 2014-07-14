import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class BrokenAlignment {

  public void main(String... args) {
    @Nullable Class object = Object.class;
    output2(object);
  }

  public static void output2(@NotNull Object value) {
    System.out.println(value);
  }
}