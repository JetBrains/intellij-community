// "Fix all 'Javadoc declaration problems' problems in file" "true"
/**
 * @throws NullPointerException description
 * @exception NullPointerException description
 * @serialData literal
 * @serialField literal
 * @return description
 */
class Test {
  /**
   * @author John Smith
   * @version 10.15.7
   * @param parameter-name description
   * @throws NullPointerException description
   * @exception NullPointerException description
   * @serialData literal
   */
  double d;

  /**
   * @version 10.15.7
   * @serial literal
   * @serialField literal
   * @return<caret> description
   */
  void bar() {}
}
