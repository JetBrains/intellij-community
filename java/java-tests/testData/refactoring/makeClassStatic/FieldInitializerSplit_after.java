class Outer {
  static class Inner {
      private final Outer anObject;
      Object x;

      public Inner(Outer anObject) {
          this.anObject = anObject;
          this.x = anObject.getClass();
      }
  }
}