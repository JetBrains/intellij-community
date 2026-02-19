class Fish {
  /*<# @Contract(value = "!null -> param1", pure = true) #>*/
  public final String/*<# ! #>*/ size(String x) {
    if (x != null) return x;
    return "|----------------------------|";
  }
}