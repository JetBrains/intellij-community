class Super {
      public static class StInner {}
      public class InstInner {}

      void foo() {
         Super.StInner inner1 = new Super.StInner();
         Super.InstInner inner2 = this.new InstInner();
      }
}