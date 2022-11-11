import org.jetbrains.annotations.NotNull;

class Test {
  public class Infer1 {
    void perform(@NotNull String s) {
      if (s == null) {
        throw new IllegalArgumentException();
      }
      System.out.println(s.length());
    }
  }
}