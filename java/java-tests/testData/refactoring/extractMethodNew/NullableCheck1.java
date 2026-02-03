class Test {
   String foo(int i, boolean flag) {
      <selection>
      String xxx = "";
        if (flag) {
            for (int j = 0; j < 100; j++) {
                if (i == j) {
                    return null;
                }
            }
        }
      </selection>
      System.out.println(xxx);
      return null;
   }
}