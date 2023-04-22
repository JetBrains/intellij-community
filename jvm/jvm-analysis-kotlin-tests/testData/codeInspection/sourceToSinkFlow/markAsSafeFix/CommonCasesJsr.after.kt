import javax.annotation.Untainted

@get:Untainted
val sOuterField: String = getSomething()

fun getSomething(): String {
  return "1"
}

internal class CommonCases {
  @field:Untainted
  private val sField: String? = null

  @Untainted
  private fun test(@Untainted s: String): String {
    val s1 = s + getS(s) + sField + sOuterField + "1".extFunc() + comObject
    return <caret>s1
  }

  @Untainted
  private fun getS(s: String): String {
    return s
  }

  companion object{
    @get:Untainted
    val comObject = getSomething2()

    private fun getSomething2(): String {
      return "1"
    }
  }
}

@Untainted
private fun String.extFunc(): String {
  return "test"
}
