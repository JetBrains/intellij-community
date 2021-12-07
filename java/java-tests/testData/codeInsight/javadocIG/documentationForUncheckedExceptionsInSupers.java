interface I {
  /**
   * @throws NullPointerException blah-blah
   */
  boolean contains(Object o);
}
interface My extends java.util.Collection, I {
  /**
   * @throws NullPointerException before {@inheritDoc} after
   */
  boolean contains(Object o) throws IOException;
}
class C {
  {
    My m = null;
    m.<caret>contains(null);
  }
}