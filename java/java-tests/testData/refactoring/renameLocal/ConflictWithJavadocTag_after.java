public class ConflictWithJavadocTag {
  /**
   * Receives some i.
   * @param i some i
   */
  public void my(int i) {
    System.out.println("i=" + i);  // print i
  }
}