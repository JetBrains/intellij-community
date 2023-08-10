public class Tes {

  public static sealed class TabNode permits Child<caret> {
  }

  public static final class ParentTabNode extends TabNode {
  }

  public static final class ChildTabNode extends TabNode {
  }
}