interface I {
  /**
   * @throws NullPointerException blah-blah
   */
  boolean contains(Object o);
}
interface My extends java.util.Collection, I {
  boolean contains(Object o);
}
class C {
  {
    My m = null;
    m.<caret>contains(null);
  }
}