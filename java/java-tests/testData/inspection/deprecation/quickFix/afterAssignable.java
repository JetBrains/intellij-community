// "Replace method call with A.newMethod" "true"
class A {
  {
    Object b = newMethod(); // invoke here
  }

  /**
   * @deprecated check if {@link #newMethod()} returns nonnull value instead
   */
  Boolean oldMethod() {
    return true;
  }

  Object newMethod() {
    return 42;
  }
}