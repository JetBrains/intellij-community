// "Merge sequential 'if' statements" "true-preview"

class Test {
  public static void main(String[] args) {
      //c3
      if (args.length == 1 || args.length == 2) {
          return; // c1
      }//c2
    System.out.println();
  }
}
