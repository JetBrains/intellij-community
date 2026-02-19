// "Cast argument to 'int'" "true-preview"
class a {
 void test(int d) {}
 
 void foo() {
   test(<caret>123_000_000_000L);
 }
}

