package pkg1;
public class AChild extends A {
  @com.google.common.annotations.VisibleForTesting
  protected void aProtectedMethod() {
    super.aProtectedMethod();
  }

  @com.google.common.annotations.VisibleForTesting
  protected void childProtectedMethod() {
  }
}