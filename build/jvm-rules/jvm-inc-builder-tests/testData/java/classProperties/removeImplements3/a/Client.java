
import javax.swing.*;

public abstract class Client {
  {
    Test test = (Test)foo();
    SwingUtilities.invokeLater(test);
  }

  public abstract Runnable foo();
}
