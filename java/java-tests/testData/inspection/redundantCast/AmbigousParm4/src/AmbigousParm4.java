import javax.swing.*;
import java.awt.*;

public class AmbigousParameter {
   public void caller() {
     new JDialog((Frame)null, "Title", true);
   }
}