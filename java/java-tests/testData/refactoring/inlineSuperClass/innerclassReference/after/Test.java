class Test {
   public void contextChild() {
      Test.StInner inner1 = new Test.StInner();
      Test.InstInner inner2 = this.new InstInner();
   }

    void foo() {
       Test.StInner inner1 = new Test.StInner();
       Test.InstInner inner2 = this.new InstInner();
    }

    public static class StInner {}

    public class InstInner {}
}