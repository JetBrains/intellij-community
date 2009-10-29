class Test {
   public void contextChild() {
      StInner inner1 = new StInner();
      Test.InstInner inner2 = this.new InstInner();
   }

    void foo() {
       StInner inner1 = new StInner();
       Test.InstInner inner2 = this.new InstInner();
    }

    public static class StInner {}

    public class InstInner {}
}