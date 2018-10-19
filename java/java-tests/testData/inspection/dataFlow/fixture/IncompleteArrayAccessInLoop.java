class X {
  void test(int[] arr, int[][] divisorsForAllNums) {
    for(int i=0; i < arr.length; i++) {
      int numInArr = arr[i];

      if(i == 0 || <error descr="Operator '<' cannot be applied to 'int', 'int[]'">numInArr < arr</error><error descr="')' expected"><error descr="Unexpected token">[</error></error><error descr="Unexpected token">]</error><error descr="Unexpected token">)</error>

      int divisorsCount = 0;
      for(int y=numInArr; y > 0; y--) {
      }

      divisorsForAllNums[i] = new int[0];
    }
  }
}