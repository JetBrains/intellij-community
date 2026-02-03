// "Change 'new X()' to 'new long()'" "false"

 class X {
 public long foo() { return <caret>new X(); }
 }
