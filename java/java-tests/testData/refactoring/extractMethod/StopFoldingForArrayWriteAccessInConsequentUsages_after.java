class Test {
  void f(int size, int[] array) {
    for (int i = 0; i < size; i++) {
      int x = i;
        newMethod(array, i, x);
    }
  }

    private void newMethod(int[] array, int i, int x) {
        int tmp = array[i];
        array[i] = array[x];
        array[x] = tmp;
    }
}