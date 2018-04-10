package test1.nested;

@org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
public class TestWithJunit4 {
  @org.junit.Test
  public void test1() {}
  
  public static class InnerNoTests {}
}