import org.checkerframework.checker.tainting.qual.Untainted

val sOuterField: String = getSomething()

fun getSomething(): String {
  return "1"
}

internal class CommonCases {
  private val sField: String? = null
  private fun test(s: String): @Untainted String {
    val s1 = s + getS(s) + sField + sOuterField + "1".extFunc() + comObject
    return <caret>s1
  }

  private fun getS(s: String): String {
    return s
  }

  companion object{
    val comObject = getSomething2()

    private fun getSomething2(): String {
      return "1"
    }
  }
}

private fun String.extFunc() = "test"
