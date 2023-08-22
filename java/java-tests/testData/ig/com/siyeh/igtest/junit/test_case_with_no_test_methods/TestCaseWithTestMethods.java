<error descr="Class 'SomeTest' is public, should be declared in a file named 'SomeTest.java'">public class SomeTest extends SomeParentClass</error> {
  public SomeTest() {
    super("");
  }
}

<error descr="Class 'SomeParentClass' is public, should be declared in a file named 'SomeParentClass.java'">public class SomeParentClass extends junit.framework.TestCase</error> {
  protected SomeParentClass(String name) {}

  public void testInParent() {}
}
