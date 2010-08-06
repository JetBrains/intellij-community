public class ConflictWithJavadocTag {
  /**
   * Receives some param.
   * @param param some param
   */
  public void my(int <caret>param) {
    System.out.println("param=" + param);  // print param
  }
}