class Test {
  void bar() {
    int size = 10;
    int array[] = new int[size];
    int array2[] = new int[size];

    int i = 0;

      newMethod(array[i], array2, i);
  }

    private void newMethod(int i, int[] array2, int i2) {
        int sum = i2;
        sum += array2[i2];
    }
}