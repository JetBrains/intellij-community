public class Test {
  public static void main(String[] args, int i) {
    if (args[i] != null) {
        extracted(args[i]);
    }
  }

    private static void extracted(String args) {
        System.out.println(args);
        System.out.println();
    }

    void test(String[] args, int i){
    if (args[i] != null) {
        extracted(args[i]);
    }
  }
}