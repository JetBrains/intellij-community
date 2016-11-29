@interface FooAnno {}
@interface BarAnno {}

public class MultipleAnnotations {
  @FooAnno
  public Object foo1() {
    return null;
  }

  @BarAnno
  public Object foo2() {
    return null;
  }
}