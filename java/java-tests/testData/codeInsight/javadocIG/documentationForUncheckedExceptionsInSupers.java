interface I {
  /**
   * @throws NullPointerException blah-blah
   */
  boolean contains(Object o) {}
}
interface My extends java.util.Collection, I {}
class C {
  {
    My m = null;
    m.<caret>contains(null);
  }
}