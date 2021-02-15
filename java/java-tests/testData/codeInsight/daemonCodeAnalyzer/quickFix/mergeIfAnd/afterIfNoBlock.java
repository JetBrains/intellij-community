// "Merge nested 'if' statements" "true"

class Test {
  public static void main(String[] args) {
      /*comment1*/
      // comment3
      if (args.length > 0 && args[/*comment2*/0].equals("foo")) {
          System.out.println("oops");
      }
  }
}
