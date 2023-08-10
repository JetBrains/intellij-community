// "Replace lambda with method reference" "true-preview"
class Example {
     static void foo() {
         Ar<String> a = String[]::new;
     }
 
     interface Ar<T> {
         T[] _(int p);
     }
}