import org.junit.Assert;

public class AnonymousClassJUnit4 {

  @org.junit.Test
  public void test2BiggerThan1() {
    new Object() {
      void foo() {
          Assert.assertTrue(2 > 1);
      }
    }
  }
}