class Test {
 void f() {
         Long L = 5L;
         if (<warning descr="Condition 'L == 5' is always 'true'">L == 5</warning>) {
             ;
         }

         long l = 0;
         if (<warning descr="Condition 'l == 0f' is always 'true'">l == 0f</warning>) {
          ;
         }

         char c = 1;
         if (<warning descr="Condition 'c == 1L' is always 'true'">c == 1L</warning>) {
           ;
         }
         
 }
}