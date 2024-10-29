// "Invert 'if' condition" "true"

class ExpressionExptected {
  public String validate(boolean b) {
      if (() || !b) {
          return null;
      }
      return "";
  }
}