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

    @NotNull
    private String newMethod() {
        String s;
        s = "";
        return s;
    }
}
