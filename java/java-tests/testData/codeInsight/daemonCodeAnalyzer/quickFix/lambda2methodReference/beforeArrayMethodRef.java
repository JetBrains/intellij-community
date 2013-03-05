// "Replace lambda with method reference" "true"
class Example {
     static void foo() {
         Ar<String> a = p -> p.c<caret>lone();
     }
 
     interface Ar<T> {
         Object _(T[] p);
     }
}