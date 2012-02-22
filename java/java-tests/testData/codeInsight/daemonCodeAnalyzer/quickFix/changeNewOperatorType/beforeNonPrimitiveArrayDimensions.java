// "Change 'new X[42]' to 'new long[]'" "true"

 class X {
 public long[] foo() { return <caret>new X[42]; }
 }
