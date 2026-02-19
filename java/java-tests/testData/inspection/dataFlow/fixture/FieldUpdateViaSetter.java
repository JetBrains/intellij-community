// IDEA-293687
public class FieldUpdateViaSetter {
  private static final MutableBoolean flag = new MutableBoolean();

  public static void main(String[] args) {
    for (int i = 0; i < 10; ++i) {
      if (flag.value && i == 0) {
        break;
      }
      if (flag.value && i == 3) {
        System.out.println("IDEA thinks we can't get here");
        break;
      }
      flag.setValue(true);
    }
  }

  private static final class MutableBoolean {
    private boolean value;

    public void setValue(boolean newValue) {
      value = newValue;
    }
  }
}