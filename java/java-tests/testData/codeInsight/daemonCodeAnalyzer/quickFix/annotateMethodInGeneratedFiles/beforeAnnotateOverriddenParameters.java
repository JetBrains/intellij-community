import org.jetbrains.annotations.NotNull;

public interface MyTestClass {
  @NotNull
  String implementMe(@NotNull<caret> String arg);
}

public class MyRealTestClass implements MyTestClass {
  String implementMe(String arg) {

  }
}