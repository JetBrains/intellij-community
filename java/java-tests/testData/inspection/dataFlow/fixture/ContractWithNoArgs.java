import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Doo {

  @NotNull
  public String doSomething() {
    String s = getSomeString();
    if (s == null) {
      throwSomeError();
    }
    return s;
  }

  private static void throwSomeError() {
    throw new RuntimeException();
  }

  @Nullable
  public String getSomeString() {
    return Math.random() > 0.5 ? null : "Yeah";
  }

}
