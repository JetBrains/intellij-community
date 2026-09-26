class EmptyLine {
  
  void x(int a) {
    System.out.println(STR."""
          a\na       "\{a}

          <warning descr="Trailing whitespace characters inside text block"><caret>      </warning>""");
  }
}