// "Replace 'if else' with '?:'" "INFORMATION"
class ValueUsedBforeOverwriting {

  void x() {
    String nullable = null;
    String a = (a = nullable(3)) == null ? nullable(2) : nullable;
  }

  private String nullable(int i) {
    return null;
  }

}