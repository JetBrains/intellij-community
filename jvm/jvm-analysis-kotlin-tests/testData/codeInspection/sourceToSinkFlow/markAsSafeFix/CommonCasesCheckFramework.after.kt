import org.checkerframework.checker.tainting.qual.Untainted

val sOuterField: @Untainted String = getSomething()

fun getSomething(): String {
  return "1"
}

internal class CommonCases {
  private val sField: @Untainted String? = null
  private fun test(s: @Untainted String): @Untainted String {
    val s1 = s + getS(s) + sField + sOuterField + "1".extFunc() + comObject
    return <caret>s1
  }

  private fun getS(s: String): @Untainted String {
    return s
  }

  companion object{
    val comObject: @Untainted String = getSomething2()

    private fun getSomething2(): String {
      return "1"
    }
  }
}

private fun String.extFunc(): @Untainted String = "test"
