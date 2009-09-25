public class Test {

   private void foo(String... args) {}

   public void bar() {
     foo("foo");
     foo("foo");
   }

   public static void main(String[] args){
     new Test().bar();
   }
}