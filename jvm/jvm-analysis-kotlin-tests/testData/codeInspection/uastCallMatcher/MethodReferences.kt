import java.util.*
import java.util.function.Function
import java.util.stream.IntStream

class MethodReferences {
  fun foo(list: List<String>) {
    list.stream().map(String::toUpperCase)
    list.stream().map<IntStream>(String::chars)
    list.stream().map(Objects::hashCode) // static method

    list.stream().map(this::bar)
    val f: Function<String, String>? = null
    list.stream().map(f!!::apply)
  }

  private fun bar(s: String): String? {
    return null
  }
}