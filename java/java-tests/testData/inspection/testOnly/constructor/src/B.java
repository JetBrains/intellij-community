import org.jetbrains.annotations.TestOnly;

public class B {
  public void foo() {
    new A();
    new A("ignore");
  }
}