
public class Junk {
  public static void main(Object xx) {
      assert xx instanceof List<?>;
    var x = (List<?>) xx;
  }
}