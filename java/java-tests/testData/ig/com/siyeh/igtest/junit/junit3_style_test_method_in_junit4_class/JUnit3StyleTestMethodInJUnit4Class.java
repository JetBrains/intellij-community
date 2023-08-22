import org.junit.Test;

public class JUnit3StyleTestMethodInJUnit4Class {

  @Test
  public void junit4Test() {
  }

  public void <warning descr="Old style JUnit test method 'testJUnit3()' in JUnit 4 class">testJUnit3</warning>() {}
}