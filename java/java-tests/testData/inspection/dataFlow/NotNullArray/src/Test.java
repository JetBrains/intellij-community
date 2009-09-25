import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

public class TestNPEafterNew {
  @Nullable Object[] arr;
  void test(@NotNull Object[] notnull) {
      arr = notnull;
      System.out.println(arr.length);
  }

  void test() {
      arr = new Object[5];
      System.out.println(arr.length);
  }
}
