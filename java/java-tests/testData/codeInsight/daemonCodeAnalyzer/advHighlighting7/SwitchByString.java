public class Test {
  private static final String BAZ = "baz";

  private void stringSwitch() {
    final String bar = "bar";
    String key = "key";
    switch (key) {
      case "": {
        System.out.println("Nothing");
        break;
      }
      case "foo": // fallthrough works as before
      case bar:   // local final variables are ok
      case BAZ: { // constants are ok
        System.out.println("Matched key");
        break;
      }
      default:
        break;
    }
  }

  private void illegalStringSwitch() {
    String foo = "foo";
    String key = "key";
    switch (key) {
      case foo:
      case <error descr="Constant expression, pattern or null is required"><error descr="Cannot resolve symbol 'getStringValue'">getStringValue</error>()</error>: {
        System.out.println("illegal");
        break;
      }
      default:
        break;
    }
  }

  private String getStringValue() {
    return "";
  }

}