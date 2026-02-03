// "Replace with record pattern" "true-preview"
class X {

  public record Example<T>(T value) {}

  public static void test(Object obj) {
    if (obj instanceof Example<?>(Object value)) {
      System.out.println(value);
    }
  }
}
