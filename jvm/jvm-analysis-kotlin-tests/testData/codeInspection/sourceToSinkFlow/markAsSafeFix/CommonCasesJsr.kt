import javax.annotation.Untainted

val sOuterField: String = getSomething()
var sOuterField2: String = getSomething()

fun getSomething(): String {
  return "1"
}

internal class CommonCases {
  public val sField: String? = null
  private fun test(s: String): @Untainted String {
    val s1 = s + getS(s) + sField + sOuterField + sOuterField2 + "1".extFunc() + "1".extFunc2(s) + comObject + comObject2
    return <caret>s1
  }

  private fun getS(s: String): String {
    return s
  }

  companion object{
    val comObject = getSomething2()
    var comObject2 = getSomething2()

    private fun getSomething2(): String {
      return "1"
    }
  }
}

private fun String.extFunc() = "test"
private fun String.extFunc2(s: String) = s
