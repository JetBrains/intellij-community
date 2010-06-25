// "Make 'call' return 'java.lang.Integer'" "true"
public class a {
 String f() {
   return new Callable<String>() {
     public String call() {
       return new Int<caret>eger(0);
     }
   }.call();
 }
}

interface Callable<T> {
  T call();
}