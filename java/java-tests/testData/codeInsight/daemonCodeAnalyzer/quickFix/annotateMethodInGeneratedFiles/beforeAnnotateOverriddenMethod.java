import org.jetbrains.annotations.NotNull;

public interface MyTestClass {
  @NotNull<caret>
  String implementMe(@NotNull String arg);
}

public class MyRealTestClass implements MyTestClass {
  String implementMe(String arg) {

  }
}