// "Convert field to local variable in constructor" "true-preview"

import javax.swing.*;

class FieldCanBeLocalTest extends JPanel {

    public FieldCanBeLocalTest() {
    super();
        String name = "MyName";
        setName(name);
  }

  public static void main(String[] args) {}
}