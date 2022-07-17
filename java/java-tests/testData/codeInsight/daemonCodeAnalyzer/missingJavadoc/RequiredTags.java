package sample;

<warning descr="Required tag '@author' is missing"><warning descr="Required tag '@param' is missing for parameter '<T>'">/**</warning></warning>
 *
 */
public class RequiredTags<T> {

  /**
   *
   */
  public void empty(){
  }

  <warning descr="Required tag '@param' is missing for parameter 'param'"><warning descr="Required tag '@return' is missing"><warning descr="Required tag '@throws' java.lang.RuntimeException is missing">/**</warning></warning></warning>
   *
   */
  public int full(int param) throws RuntimeException{
      return 42;
  }

}
