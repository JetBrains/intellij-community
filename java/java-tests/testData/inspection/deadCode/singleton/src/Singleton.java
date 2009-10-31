pubic class Singleton {
   private static Singleton ourInstance = new Singleton();

   public Singleton getInstance() {
      return ourInstance;
   }
}