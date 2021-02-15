// "Merge nested 'if' statements" "true"

class Test {
  public static void main(String[] args) {
    i<caret>f(args.length > 0/*comment1*/)
      // comment3
      if(args[/*comment2*/0].equals("foo")) {
        System.out.println("oops");
      }
  }
}
