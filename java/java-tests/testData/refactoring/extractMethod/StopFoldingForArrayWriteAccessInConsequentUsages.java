class Test {
  void f(int size, int[] array) {
    for (int i = 0; i < size; i++) {
      int x = i;
      <selection>int tmp = array[i];
      array[i] = array[x];
      array[x] = tmp;</selection>
    }
  }
}