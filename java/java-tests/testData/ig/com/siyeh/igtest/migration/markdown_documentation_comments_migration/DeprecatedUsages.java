import java.lang.Deprecated;
public class DeprecatedUsages {
  
  <warning descr="Javadoc comment can be Markdown documentation comment">/**<caret></warning>
   * @deprecated We before java 5 there were no annotations 
   * so that how you were expected to deprecate something 
   */
  void onlyTag() {}
  
  <warning descr="Javadoc comment can be Markdown documentation comment">/**</warning> @deprecated */
  void emptyTag() {}
  
  <warning descr="Javadoc comment can be Markdown documentation comment">/**</warning> @deprecated */
  @Deprecated
  void emptyTagWithAnnotation() {}
  
  
  <warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
   * @deprecated Markdown Javadoc updated the meaning of the deprecated tag. 
   * This is a bit of heresy and honestly artificial friction but so be it. 
   */
  @Deprecated
  void tagWithAnnotation() {}
} 