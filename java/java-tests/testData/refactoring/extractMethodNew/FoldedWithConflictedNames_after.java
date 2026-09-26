class Test {
  void bar() {
    int size = 10;
    int array[] = new int[size];
    int array2[] = new int[size];

    int i = 0;

      newMethod(array[i], array2, i);
  }

    private void newMethod(int sum1, int[] array2, int i) {
        int sum = sum1;
        sum += array2[i];
    }
}