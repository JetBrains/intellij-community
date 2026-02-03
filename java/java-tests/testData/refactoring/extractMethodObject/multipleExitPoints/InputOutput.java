class Test {
   void foo() {
      int i = 0;
        int j = 0;
        <selection>
        int k =  i + j;
        j = 9;
        </selection>
        int n = i+j;
        int m = k;
   }
}