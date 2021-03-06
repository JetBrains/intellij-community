// "Cast parameter to 'double'" "true"
class a {
 void test(Double d) {}
 
 void foo() {
   test((double) 0x123);
 }
}

