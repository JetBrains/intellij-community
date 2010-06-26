public class Test {
  private static final String BAZ = "baz";

  private void stringSwitch() {
      final String bar = "bar";
      String key = "key";
      switch (<error>key</error>) {
          case "": {
              System.out.println("Nothing");
              break;
          }
          case "foo":
          case bar:
          case BAZ: {
              System.out.println("Matched key");
              break;
          }
          default:
              break;
      }
  }

}