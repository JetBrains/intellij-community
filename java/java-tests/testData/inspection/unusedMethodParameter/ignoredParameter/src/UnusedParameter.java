import java.awt.Button;
import java.awt.event.ActionEvent;
public class UnusedParameter {
   public UnusedParameter() {
      Button btn = new Button();
      btn.addActionListener(UnusedParameter::onAction);
   }

   private static void onAction(ActionEvent ignoredEvent) {
      System.out.println("Test");
   }
}
