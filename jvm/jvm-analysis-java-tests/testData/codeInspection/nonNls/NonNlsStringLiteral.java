import org.jetbrains.annotations.NonNls;

public class NonNlsStringLiteral {
  @NonNls String nonNlsField = "nonNlsFieldValue";

  @NonNls String nonNlsMethod() {
    return "valueReturnedFromNonNlsMethod";
  }

  void method() {
    nonNlsField = "nonNlsFieldNewValue";

    @NonNls String nonNlsVar = "nonNlsVarValue";
    nonNlsVar.concat("argumentForMethodWithNonNlsVarReceiver");

    nonNlsField.concat("argumentForMethodWithNonNlsFieldReceiver");
  }

  void checkEquals(@NonNls String nonNlsParam) {
    nonNlsField.equals("argumentToEqualsOnNonNlsField");
    nonNlsParam.equals("argumentToEqualsOnNonNlsParamInCheckEquals");
    @NonNls String nonNlsVar = "nonNlsVarValueInCheckEquals";
    nonNlsVar.equals("argumentToEqualsOnNonNlsVar");
  }

  void methodWithNonNlsParam(@NonNls String nonNlsParam) {
    nonNlsParam.concat("argumentForMethodWithNonNlsParamReceiver");
    nonNlsParam = "nonNlsParamValue";
  }
}