// "Change 'new X[42]' to 'new long[]'" "true-preview"

 class X {
 public long[] foo() { return new long[42]; }
 }
