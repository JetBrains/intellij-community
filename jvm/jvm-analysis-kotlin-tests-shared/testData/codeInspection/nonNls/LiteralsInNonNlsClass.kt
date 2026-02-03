import org.jetbrains.annotations.NonNls

@NonNls
class LiteralsInNonNlsClass {
  var field = "field"

  fun method(param: String = "paramDefaultValue"): String {
    field = "field"
    val variable = "var"
    variable.plus("plus")
    variable == "equalityOperator"
    variable.equals("equals")
    return "return"
  }
}