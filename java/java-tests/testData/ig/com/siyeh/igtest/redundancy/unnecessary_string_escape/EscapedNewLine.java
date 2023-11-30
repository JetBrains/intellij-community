class EscapedNewLine {
  public static void main(String[] args) {
    String s = """
                first line
                          a<warning descr="'\n' is unnecessarily escaped"><caret>\n</warning>|
                third line
                """;
    System.out.println(s.replace(' ', '.'));
    System.out.println("123");
  }
}