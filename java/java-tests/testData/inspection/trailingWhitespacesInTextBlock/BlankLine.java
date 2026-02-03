class BlankLine {
  
  void x() {
    String s = STR."""
		  
        there     is something<warning descr="Trailing whitespace characters inside text block"><caret>  </warning>
        """;
  }
}