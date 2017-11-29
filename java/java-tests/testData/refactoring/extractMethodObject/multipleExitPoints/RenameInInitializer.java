public class RenameInInitializer {
  public static void main(String[] args) {
    <selection>int aValue = 100;
    int result = 1000;
    int a = IntStream.of(aValue, result).peek(x -> {}).sum();</selection>
    System.out.println(aValue + result + a);
  }
}
