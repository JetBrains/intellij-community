import javax.swing.*;
import java.awt.*;

public class Bar {
  public static void main(String[] args) {
    boolean retrere = false;
    foo(args == null ? true : retrere<caret>)
  }
}

