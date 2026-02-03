import static org.junit.Assert.assertTrue;

class MyTest {

  public void testObjectsEquals() {
      <warning descr="'assertTrue()' can be simplified to 'assertFalse()'"><caret>assertTrue</warning>("message", !(1 == 2));
  }
}