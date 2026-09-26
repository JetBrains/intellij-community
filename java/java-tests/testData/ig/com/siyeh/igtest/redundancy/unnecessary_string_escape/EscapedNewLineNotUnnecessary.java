class EscapedNewLineNotUnnecessary {
  public static void main(String[] args) {
    String s = """
                first line
                          \n
                third line
                """;
    System.out.println(s.replace(' ', '.'));
    System.out.println("123");
  }
}