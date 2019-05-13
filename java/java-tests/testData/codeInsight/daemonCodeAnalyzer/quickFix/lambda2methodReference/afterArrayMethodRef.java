// "Replace lambda with method reference" "true"
class Example {
     static void foo() {
         Ar<String> a = String[]::clone;
     }
 
     interface Ar<T> {
         Object _(T[] p);
     }
}