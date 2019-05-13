import org.jetbrains.annotations.NotNull;

public interface MyTestClass {
  @NotNull
  String implementMe(@NotNull String arg);
}

public class MyRealTestClass implements MyTestClass {
  String implementMe(@NotNull String arg) {

  }
}