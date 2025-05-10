import org.jetbrains.annotations.NonNls

@field:NonNls
var topLevelVar = "topLevelNonNlsVarValue"

class NonNlsStringLiteral {
  @field:NonNls
  var nonNlsField = "nonNlsFieldValue"
  @field:NonNls
  var nonNlsMultilineField = """
    nonNlsMultilineFieldValue
  """

  @NonNls
  fun nonNlsMethod(): String {
    return "valueReturnedFromNonNlsMethod"
  }

  fun method() {
    nonNlsField = "nonNlsFieldNewValue"

    @NonNls val nonNlsVar = "nonNlsVarValue"
    nonNlsVar.plus("argumentForMethodWithNonNlsVarReceiver")

    nonNlsField.plus("argumentForMethodWithNonNlsFieldReceiver")
  }

  fun checkEquals(@NonNls nonNlsParam: String) {
    nonNlsField.equals("argumentToEqualsOnNonNlsField")
    nonNlsField == "argumentToEqualityOperatorOnNonNlsField"

    nonNlsParam.equals("argumentToEqualsOnNonNlsParamInCheckEquals")
    nonNlsParam == "argumentToEqualityOperatorOnNonNlsParamInCheckEquals"

    @NonNls val nonNlsVar = "nonNlsVarValueInCheckEquals"
    nonNlsVar.equals("argumentToEqualsOnNonNlsVar")
    nonNlsVar == "argumentToEqualityOperatorOnNonNlsVar"
  }

  fun methodWithNonNlsParam(@NonNls nonNlsParam: String = "nonNlsParamDefaultValue") {
    nonNlsParam.plus("argumentForMethodWithNonNlsParamReceiver")
  }
}