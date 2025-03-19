class X {
  public static void main(String[] args) {
    var a = switch (new Object()){
      case Object object -> """
        hello<caret>
        """;
    };
  }
}
