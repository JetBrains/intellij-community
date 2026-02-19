public class Test {
    String test(String s){
        return switch (s) {
          case "a" -> {
            <selection>System.out.println();
            yield "one";</selection>
          }
          case "b" -> {
            System.out.println();
            yield "two";
          }
          default -> "three";
        };
    }
}