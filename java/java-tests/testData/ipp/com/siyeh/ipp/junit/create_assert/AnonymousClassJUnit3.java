import junit.framework.TestCase;

public class AnonymousClassJUnit3 extends TestCase {

  public void test2BiggerThan1() {
    new Object() {
      void foo() {
        2 > 1<caret>
      }
    };
  }
}