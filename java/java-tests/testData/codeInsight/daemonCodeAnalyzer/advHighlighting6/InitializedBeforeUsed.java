class Test {
  public static void main(String[] args) {
    int i;
    int[] iA = {10,20};
    iA[<error descr="Variable 'i' might not have been initialized">i</error>] = i = 30;
  }
}