import org.jetbrains.annotations.NotNull;

interface JSType {
}
class JSTypeofTypeImpl implements JSType {
  static final JSType NO_TYPE = new JSType() {
  };
  private JSType myEvaluatedType;
  private void evaluateType() {
    JSType exprType = JSResolveUtil.getExpressionJSType();
    if (exprType instanceof JSTypeofTypeImpl) {
    }
    myEvaluatedType = NO_TYPE;
  }

}

class JSResolveUtil {

  @NotNull
  public static JSType getExpressionJSType() {
    return JSTypeofTypeImpl.NO_TYPE;
  }
}