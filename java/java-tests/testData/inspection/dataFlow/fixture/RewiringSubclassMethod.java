// IDEA-302493
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RewiringSubclassMethod {
  void test(Parent parent) {
    if (parent instanceof C1 || parent instanceof C2) {
      String s = parent.readString();
      System.out.println(s.trim());
      String s2 = parent.getString();
      System.out.println(s2.trim());
    }
  }

  interface Parent {
    @Nullable String readString();
    @Nullable String getString();
  }

  interface C1 extends Parent {
    @NotNull String readString();
    @NotNull String getString();
  }

  interface C2 extends Parent {
    @NotNull String readString();
    @NotNull String getString();
  }
}