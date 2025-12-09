/*<# block fmt:fontSize=ABitSmallerThanInEditor,marginPadding=OnlyPadding #>*/
class Test {
  /*<# @Contract(value = "!null -> param1", pure = true) #>*/
  public final String/*<# ! #>*/ bar(String x) {
    if (x != null) return x;
    return "|----------------------------|";
  }
}