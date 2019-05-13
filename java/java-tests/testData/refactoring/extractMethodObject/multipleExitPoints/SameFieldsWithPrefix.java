public class SameFieldsWithPrefix {
  public static void main(String[] args) {
    <selection>int aValue = 100;
    int bValue = 1000;

    IntStream.of(aValue, bValue).peek(x -> {}).sum();</selection>

    System.out.println(aValue + bValue);
  }
}
