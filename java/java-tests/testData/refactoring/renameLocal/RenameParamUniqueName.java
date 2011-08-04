import java.awt.*;

/**
 * @author pegov
 */
public class MacMessages {


  public static void showOkMessageDialog(String title, String message, String okText, Window co<caret>mponent) {
    showMessageDialog(title, okText, null, null, message, component);
  }



  public static int showMessageDialog(String title,
                                    String okText,
                                    String alternateText,
                                    String cancelText,
                                    String message,
                                    Window window) {
    return 1;
  }

}