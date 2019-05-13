abstract class Test {
  void testSameEquals(int[] arr1, int[] arr2) {
    if (arr1[0] == arr2[0] && <warning descr="Condition 'arr1[0] == arr2[0]' is always 'true' when reached">arr1[0] == arr2[0]</warning>) {
    }
  }

  void testMatrices(int[][][] arr1, int[][][] arr2) {
    if (arr1[0][1][2] == arr1[0][1][3] &&
        <warning descr="Condition 'arr1[0][1][2] == arr1[0][1][3]' is always 'true' when reached">arr1[0][1][2] == arr1[0][1][3]</warning>) {
    }
  }

  void testNotEquals(int[] arr1, int[] arr2) {
    if (arr1[0] != arr2[0]) return;

    if (<warning descr="Condition 'arr1[0] == arr2[0]' is always 'true'">arr1[0] == arr2[0]</warning>) {}
  }

  void testInvalidatingCall(int[] arr1, int[] arr2) {
    if (arr1[0] != arr2[0]) return;

    changeArray(arr1);

    if (arr1[0] == arr2[0]) {}
  }
  abstract void changeArray(int[] array);


  void testIndirectlyInvalidatingCall(int[] arr1, int[] arr2, int[][] arr3) {
    arr3[0] = arr1;

    if (arr1[0] != arr2[0]) return;

    changeSubArray(arr3);

    if (arr1[0] == arr2[0]) {}
  }
  abstract void changeSubArray(int[][] array);


  void testAssigning(int[] arr1, int j) {
    arr1[0] = j;
    if (<warning descr="Condition 'arr1[0] == j' is always 'true'">arr1[0] == j</warning>) { }
  }

  void testReassigning(int[] arr1) {
    arr1[0] = 1;
    arr1[1] = 3;
    if (<warning descr="Condition 'arr1[0] == 1' is always 'true'">arr1[0] == 1</warning>) { }

    for (int i = 0; i < arr1.length; i++) {
      arr1[i] = 2;
    }
    if (arr1[0] == 1) { }
  }
}