import org.checkerframework.checker.tainting.qual.Untainted

internal class CommonCases {
  public val sField: String? = null
  fun test(s: @Untainted String): @Untainted String {
    val s1 = s + getS(s) + sField + "1".extFunc() + "1".extFunc2(s) + comObject2
    return <caret>s1
  }

  fun getS(s: String): @Untainted String {
    return s
  }

  companion object{
    var comObject2: @Untainted String = getSomething2()

    private fun getSomething2(): String {
      return "1"
    }
  }
}

private fun String.extFunc() = "test"
private fun String.extFunc2(s: String): @Untainted String = s
