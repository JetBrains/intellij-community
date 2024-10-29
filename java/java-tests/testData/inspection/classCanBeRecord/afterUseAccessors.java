// "Convert to record class" "true"
public record SpreadSymbol(String symbol, int legs) {

    void fromClass() {
        System.out.println(symbol);
        System.out.println(legs);
    }
}

class Use {
  /**
   * You may also want to use {@link SpreadSymbol#legs()}.
   *
   * @see SpreadSymbol#legs()
   */
  public static void main(String[] args) {
    SpreadSymbol ss = new SpreadSymbol("123", 123);
    System.out.println(ss.symbol());
    System.out.println(ss.legs());
  }
}