// "Replace method call with A.newMethod" "false"
class A {
  {
    boolean b = oldMeth<caret>od(); // invoke here
  }

  /**
   * @deprecated check if {@link #newMethod()} returns nonnull value instead
   */
  boolean oldMethod() {
    return true;
  }

  Object newMethod() {
    return 42;
  }
}