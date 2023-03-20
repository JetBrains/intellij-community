// "Fix all 'Javadoc declaration problems' problems in file" "true"
class Test {
  /**
   * @throws java.io.<caret>IOException description
   * @throws
   */
  void bar() {}
}
