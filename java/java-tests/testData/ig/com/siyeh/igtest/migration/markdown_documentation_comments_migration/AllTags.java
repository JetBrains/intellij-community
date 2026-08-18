<warning descr="Javadoc comment can be Markdown documentation comment">/**<caret></warning>
   * {@summary Sample method demonstrating all Javadoc tags}<br>
   * {@return calculated result}<br>
   * {@code Math.E}<br>
   * {@literal <T>}<br>
   * {@link Math#E}<br>
   * {@linkplain Math#E Euler constant}<br>
   * {@index calculation}<br>
   * {@systemProperty java.version}<br>
   * {@value Math#E}
   * {@snippet : return a + b;}
   * @param <T> type parameter
   * @param a first parameter
   * @param b second parameter
   * @return sum result
   * @throws ArithmeticException overflow
   * @exception IllegalArgumentException invalid input
   * @see Math#E
   * @since 1.0
   * @author John Doe
   * @version 2.0
   * @deprecated use {@link Math#addExact}
   * @serial include
   * @serialData writes int, double
   * @serialField value double field
   * @spec <a href="https://example.com">link</a> spec
   * @provides Math
   * @uses Math
   * @hidden
   */
class AllTags {
  
  <warning descr="Javadoc comment can be Markdown documentation comment">/**<caret></warning>
   * {@literal <T>}
   */
  void literal() {}
  
  <warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
   * {@code <T>}
   */
  void code(){}
  
  <warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
   * {@snippet : 
   *  List<String> toto = new ArrayList<>();
   * }
   */
  void snippet(){}
  
  
  <warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
   * <pre>&commat;Example
   *     public void htmlCharEntity() {
   *     }
   * }</pre>
   */
  void fakeTag(){}
}