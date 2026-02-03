// "Escape trailing whitespace characters" "true"

class StringTemplate2 {
  String s = STR."""
          before\{}<warning descr="Trailing whitespace characters inside text block"><caret> </warning>
          \{}after""";
}