// "Merge sequential 'if's" "true"

class Test {
  public static void main(String[] args) {
    i<caret>f (args.length == 1) {
      return; // c1
    }
    else if (args.length == 2) {
      return; //c2
    }
    System.out.println();
  }
}
