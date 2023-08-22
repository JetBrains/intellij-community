class Test {
  int[] a = new int[10];
  
    void foo() {
        int log = 0;
        while (true) {
            int temp = a.length;
            if (!(1 << log < temp)) break;
            log++;
        }
    }
}