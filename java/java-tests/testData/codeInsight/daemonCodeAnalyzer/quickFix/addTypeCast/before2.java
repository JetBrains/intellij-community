// "Cast to 'b'" "true"
class a {
 void f(a a) {
   <caret>b b = a;
 }
}
class b extends a {}
