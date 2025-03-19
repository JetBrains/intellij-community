// "Convert argument to 'long'" "true-preview"
class a {
 void test(Long l) {}
 
 void foo() {
   test(<caret>123);
 }
}

