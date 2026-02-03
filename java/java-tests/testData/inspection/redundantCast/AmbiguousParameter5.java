class AmbiguousParameter {
   public void caller() {
     new JDialog( ((Frame)null), "Title", true);
   }
}

class Frame {}
class Window extends Frame {}
class Dialog extends Window {}
class JDialog extends Dialog {
  public JDialog(Frame owner, String title, boolean modal) {}
  public JDialog(Dialog owner, String title, boolean modal) {}
}