import java.util.*;

public class CollectionTest {
  /**
   * Normal JavaDoc, can
   *         be
   *                    formatted.
   * @param args
   *                Arguments.
   */
  public static void main(String[] args) {
    int size;
    HashSet collection = new HashSet();
    // @formatter:off
    String str1 = "Yellow",
            str2 = "White",
             str3 = "Green",
              str4 = "Blue";
    // @formatter:on
    if        (true)
          System.out.println("True");
    Iterator iterator;
    // @formatter:off
    if    (true)
          System.out.println("True");
    collection.add(str1);
      collection.add(str2);
        collection.add(str3);
    // @formatter:on
    collection.add(str4);
    iterator = collection.iterator();
    while (iterator.hasNext()) {
      System.out.print(iterator.next() + " ");
    }
  }

  // @formatter:off
  /**
   * And please don't touch this:
   *   @param   x
   *     These are my nice comments.
   *   @param   y
   *     And yet another one.
   */
  public void doSomething(String x, String y) {
    if
       (true)
          System.out.println("True");
  }
  // @formatter:on

  /**
   * It's OK to format this comment.
   *    @param   z
   *       Parameter Z.
   */
  public void doSomethingElse(String z) {
    if    (true)
          System.out.println("True");
  }

  // @formatter:off
  /**
   *   This comment must be preserved too.
   *   @param i
   *      Parameter I.
   *   @param j
   *      Parameter J.
   */
  public void evenMore(int i, int j) {
    if
       (true)
          System.out.println("True");
  }
  // @formatter:on
}
