class BadTextBlock {
  
  void x() {
    String s = <error descr="Illegal text block start: missing new line after opening quotes">"""</error>a
      bad bad bad   
      """;
  }
}