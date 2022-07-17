public class MyMockitoTest {
  @org.mockito.Mock
  private String myFoo;

  {
    System.out.println(myFoo);
  }

  @org.junit.Test
  public void testName() throws Exception {}
}
