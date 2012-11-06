class Outer {
  static class Inner {
    Object x;

      private Outer anObject;

      public Inner(Outer anObject) {
          this.anObject = anObject;
          this.x = anObject.getClass();
      }
  }
}