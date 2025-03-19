import org.jetbrains.annotations.Nullable;

public class Test {
    String test(String s){
        return switch (s) {
          case "a" -> {
              String one = getString();
              if (one != null) yield one;
              yield "default";

          }
          default -> "two";
        };
    }

    private static @Nullable String getString() {
        if (new Random().nextBoolean()) {
          System.out.println();
            return "one";
        }
        return null;
    }
}