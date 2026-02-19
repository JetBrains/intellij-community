public class Test {
    String test(String s){
        return switch (s) {
          case "a" -> {
            <selection>if (new Random().nextBoolean()) {
              System.out.println();
              yield "one";
            }</selection>
            yield "default";

          }
          default -> "two";
        };
    }
}