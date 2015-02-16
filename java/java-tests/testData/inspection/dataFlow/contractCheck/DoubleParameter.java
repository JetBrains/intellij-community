import org.jetbrains.annotations.Contract;

class Zoo {
  @Contract("null, _ -> null; !null, _ -> !null")
  public static Double testContract_1(Double value1, int value2) {
    if (value1 == null) {
      return null;
    }
    return 0.0;
  }
}