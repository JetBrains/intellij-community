// "Replace lambda with method reference" "true-preview"
class Example {
     static void foo() {
         Ar<String> a = p -> p.c<caret>lone();
     }
 
     interface Ar<T> {
         Object _(T[] p);
     }
}