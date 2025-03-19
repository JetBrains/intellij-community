public class Singleton {
   private static Singleton ourInstance = new Singleton();

   public static Singleton getInstance() {
      return ourInstance;
   }
}