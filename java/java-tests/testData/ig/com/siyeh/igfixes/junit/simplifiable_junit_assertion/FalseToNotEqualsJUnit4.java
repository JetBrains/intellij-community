import static org.junit.Assert.assertFalse;

class DoublePrimitive {

  public void testPrimitive() {
      <warning descr="'assertFalse()' can be simplified to 'assertNotEquals()'"><caret>assertFalse</warning>(doubleValue().equals(2.0));
  }

  Double doubleValue() {
    return 1.0;
  }
}