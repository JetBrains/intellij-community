import org.jetbrains.annotations.NotNull;

public class S {
  {
    String s;
    try {
        s = newMethod();
    } finally {
    }
    System.out.print(s);
  }

    private @NotNull String newMethod() {
        String s;
        s = "";
        return s;
    }
}
