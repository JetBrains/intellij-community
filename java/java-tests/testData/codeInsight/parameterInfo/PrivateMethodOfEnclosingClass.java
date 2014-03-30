public abstract class Base {

   public abstract void func();

   public static class Impl extends Base {
     @Override
     public void func() {
       foo(<caret>);
     }
   }

   private static boolean foo(String param) {
     return true;
   }
}
