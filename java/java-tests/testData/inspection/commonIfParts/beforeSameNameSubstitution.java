// "Extract variables from 'if'" "true"


public class Main {
  // https://youtrack.jetbrains.com/issue/IDEA-229916
  public void analysisBugWithMinAndMax(int width, int height, boolean someFlag) {
    if<caret> (someFlag) {
      int a = Math.max(width, height);
      int b = Math.min(width, height);
      System.out.println("a=" + a + ", b=" + b);
    }
    else {
      int b = Math.max(width, height);
      int a = Math.min(width, height);
      System.out.println("a=" + a + ", b=" + b);
    }
  }
}