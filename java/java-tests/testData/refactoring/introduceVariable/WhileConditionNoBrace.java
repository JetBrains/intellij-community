class Test {
  int[] a = new int[10];
  
    void foo() {
        int log = 0;
        while (1 << log < <selection>a.length</selection>) log++;
    }
}