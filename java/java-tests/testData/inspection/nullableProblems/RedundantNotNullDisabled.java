import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNullByDefault;
import java.util.List;

@NotNullByDefault
class FromDemo {
  @NotNull String m(@NotNull String s, @Nullable String s2) {
    return "";
  }
  
  @NotNull String f;
  
  List<@NotNull String> param(@NotNull String @NotNull [] @NotNull [] a) {
    return List.of();
  }

  void fun() {
    @Nullable String variableWhereAnnotationMakesSense = OtherClass.returnSomething();
  }
}

class OtherClass {
  static String returnSomething() {
    return Math.random() > 0.5 ? "not null" : null;
  }
}
