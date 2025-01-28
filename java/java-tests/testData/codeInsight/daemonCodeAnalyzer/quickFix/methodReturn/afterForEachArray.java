// "Make 'getData()' return 'int[]'" "true-preview"
class Test {
  public static void main(String[] args) {
    for (int <caret>arg : getData()) {

    }
  }

  private static int[] getData() {
    return new String[]{};
  }
}
