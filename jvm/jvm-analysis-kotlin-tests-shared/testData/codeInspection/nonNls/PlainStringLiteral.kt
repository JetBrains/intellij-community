var topLevelVar = "topLevelVar"
class PlainStringLiteral {
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