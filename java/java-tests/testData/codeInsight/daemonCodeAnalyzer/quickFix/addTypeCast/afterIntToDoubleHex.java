// "Cast argument to 'double'" "true-preview"
class a {
 void test(Double d) {}
 
 void foo() {
   test((double) 0x123);
 }
}

