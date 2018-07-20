// "Merge sequential 'if's" "true"

class Test {
  public static void main(String[] args) {
    i<caret>f (args.length == 1) {
      return; // c1
    }//c2
    else if (args.length == 2) {
      return; //c3
    }
    System.out.println();
  }
}
