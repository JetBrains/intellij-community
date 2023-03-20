import org.jetbrains.annotations.NotNull;

public class Test {
    String test(String s){
        return switch (s) {
          case "a" -> {
              yield getString("one");
          }
          case "b" -> {
              yield getString("two");
          }
          default -> "three";
        };
    }

    private static String getString(String one) {
        System.out.println();
        return one;
    }
}