import org.checkerframework.checker.tainting.qual.Untainted

class KotlinConstructor(private val field: String = ""){
  fun test() {
    sink(<warning descr="Unknown string is used as safe parameter">field</warning>)
  }
  fun sink(<warning descr="[UNUSED_PARAMETER] Parameter 's' is never used">s</warning>: @Untainted String?) {}
}
