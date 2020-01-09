// "Extract variables from 'if'" "true"


public class Main {
  // https://youtrack.jetbrains.com/issue/IDEA-229916
  public void analysisBugWithMinAndMax(int width, int height, boolean someFlag) {
      int a = Math.max(width, height);
      int b = Math.min(width, height);
      if (someFlag) {
          System.out.println("a=" + a + ", b=" + b);
    }
    else {
          System.out.println("a=" + b + ", b=" + a);
    }
  }
}