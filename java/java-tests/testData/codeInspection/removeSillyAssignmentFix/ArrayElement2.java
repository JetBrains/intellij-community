class X {
  void a(int[] arr) {
    arr[arr.length - 1] = <caret>arr[arr.length - 1] = 1;
  }
}