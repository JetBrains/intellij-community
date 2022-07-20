// "Change 'new X[42]' to 'new long[]'" "true-preview"

 class X {
 public long[] foo() { return <caret>new X[42]; }
 }
