
class A implements <error descr="com.mycom.FirstPreviewFeature is a preview API and may be removed in a future release">com.mycom.FirstPreviewFeature</error> {
  public void f() {}

  static class B implements <warning descr="com.mycom.FirstPreviewFeatureReflective is a preview API and may be removed in a future release">com.mycom.FirstPreviewFeatureReflective</warning> {
    public void f() {}
  }
}
