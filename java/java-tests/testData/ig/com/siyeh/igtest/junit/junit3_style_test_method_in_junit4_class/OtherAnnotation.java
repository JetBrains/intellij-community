import org.junit.*;

public class OtherAnnotation {

  @Test
  public void testFoo() {}
  
  public void <warning descr="Old style JUnit test method 'testSmth()' in JUnit 4 class">testSmth</warning>() {}
  
  @Ignore
  public void testIgnored() {}
  
  @After
  public void testAfter() {}
  
  @Before
  public void testBefore() {}
}