// "Replace lambda with method reference" "true"
class Example {
     static void foo() {
         Ar<String> a = p -> new <caret>String[p];
     }
 
     interface Ar<T> {
         T[] _(int p);
     }
}