import org.checkerframework.checker.tainting.qual.Untainted

class MethodAsFieldTest {

  fun test(clean: @Untainted MethodAsFields, unclean: MethodAsFields) {
    sink(clean.t)
    sink(<warning descr="Unknown string is used as safe parameter">unclean.t</warning>) //warn
  }

  fun sink(<warning descr="[UNUSED_PARAMETER] Parameter 'string' is never used">string</warning>: @Untainted String?) {}
}