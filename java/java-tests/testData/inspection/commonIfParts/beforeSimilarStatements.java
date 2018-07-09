// "Extract common part from if " "false"

public class Main {
  public static void main(String[] args) {
    int x = 12;
    StringBuilder builder = new StringBuilder();
    if<caret>(x > 0) {
      builder.append("{");
      builder.append(x);
      builder.append("}");
    } else {
      builder.append("{");
      builder.append(0);
      builder.append("}");
    }
  }
}