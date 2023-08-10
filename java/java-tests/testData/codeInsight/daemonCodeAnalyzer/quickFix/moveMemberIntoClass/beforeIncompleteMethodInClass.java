// "Move member into class" "false"

public class beforeIncompleteMethodInClass {
  private String testInt;

  public beforeIncompleteMethodInClass(int testInt) {
    this.testInt = "aaaa";
  }
  
  public String getTestInt() {
    return testInt;
  }

  public void setTestInt(String testInt) {
    this.testInt = testInt;
  }

  public<caret>
}