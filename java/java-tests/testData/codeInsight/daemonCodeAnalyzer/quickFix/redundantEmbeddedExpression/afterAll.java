// "Fix all 'Redundant embedded expression in string template' problems in file" "true"
class Test {
  public static void main(String[] args) {
    System.out.println(STR."hello|null|world");
    System.out.println(STR."hello|null|world");
    System.out.println(STR."hello||world");
    System.out.println(STR."hello|\r\n|world");
      /*before*/
      /*after*/
      System.out.println(STR."hello|str|world \{1 + 1}");
    System.out.println(STR."""
    			Hello!!! \{"""
                 World"""}""");
    System.out.println(STR."hello|1|world \{1 + 1}");
    System.out.println(STR."hello|1000|world \{1 + 1}");
    System.out.println(STR."hello|\{1_000}|world \{1 + 1}");
    System.out.println(STR."hello|1.0|world \{1 + 1}");
    System.out.println(STR."hello|\{1.0d}|world \{1 + 1}");
  }
}
