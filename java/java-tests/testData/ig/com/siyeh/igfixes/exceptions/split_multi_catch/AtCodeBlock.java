import java.io.*;

public class AtCodeBlock {
  void foo() {
    try {
      Reader reader = new FileReader("");
    } catch (IndexOutOfBoundsException | FileNotFoundException e) {<caret>
    }
  }
}