// "Make 'call' return 'java.lang.Integer'" "true"
public class a {
 String f() {
   return new Callable<Integer>() {
     public Integer call() {
       return new Integer(0);
     }
   }.call();
 }
}

interface Callable<T> {
  T call();
}