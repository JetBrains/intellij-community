// "Merge sequential 'if's" "true"

class Test {
  public static void main(String[] args) {
      //c2
      if (args.length == 1 || args.length == 2) {
          return; // c1
      }
    System.out.println();
  }
}
