// "Invert 'if' condition" "true"

class ExpressionExptected {
  public String validate(boolean b) {
    if <caret>(!() && b) {
      return "";
    }
    return null;
  }
}