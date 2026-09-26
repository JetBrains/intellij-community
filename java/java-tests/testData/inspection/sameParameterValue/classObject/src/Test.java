public class Test {

   private void foo(Class<?> clz) {}

   public void bar() {
     foo(String.class);
     foo(String.class);
   }

   public static void main(String[] args){
     new Test().bar();
   }
}