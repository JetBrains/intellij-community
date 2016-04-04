// "Convert to local" "true"

import javax.swing.*;

class FieldCanBeLocalTest extends JPanel {

  private String na<caret>me = "MyName";

  public FieldCanBeLocalTest() {
    super();
    setName(name);
  }

  public static void main(String[] args) {}
}