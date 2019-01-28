// "Convert field to local variable in method 'FieldCanBeLocalTest'" "true"

import javax.swing.*;

class FieldCanBeLocalTest extends JPanel {

    public FieldCanBeLocalTest() {
    super();
        String name = "MyName";
        setName(name);
  }

  public static void main(String[] args) {}
}