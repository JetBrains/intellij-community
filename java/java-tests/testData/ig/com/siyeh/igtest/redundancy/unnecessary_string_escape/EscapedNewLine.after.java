class EscapedNewLine {
  public static void main(String[] args) {
    String s = """
                first line
                          a
                |
                third line
                """;
    System.out.println(s.replace(' ', '.'));
    System.out.println("123");
  }
}