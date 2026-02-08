
import javax.swing.*;

public abstract class Client {
  {
    Test test = foo();
    SwingUtilities.invokeLater(test);
  }

  public abstract Test foo();
}
