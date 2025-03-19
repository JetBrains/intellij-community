import javax.annotation.Untainted

internal class CommonCases {
  public val sField: String? = null

  @Untainted
  fun test(@Untainted s: String):  String {
    val s1 = s + getS(s) + sField + "1".extFunc() + "1".extFunc2(s) + comObject2
    return <caret>s1
  }

  @Untainted
  fun getS(s: String): String {
    return s
  }

  companion object{
    @field:Untainted
    var comObject2 = getSomething2()

    private fun getSomething2(): String {
      return "1"
    }
  }
}

private fun String.extFunc() = "test"
@Untainted
private fun String.extFunc2(s: String) = s
