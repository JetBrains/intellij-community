import org.jetbrains.annotations.NotNull;

public class Market {

  public void a() {
    System.out.println(getGoods()[0]);
    System.out.println(getGoods()[1]);
    System.out.println(getGoods()[1].charAt(0));
    System.out.println(getGoods()[0].charAt(0));
    System.out.println(getGoods()[0]+ getGoods()[1]);
  }

  @NotNull
  private static String[] getGoods() {
    return new String[]{"orange", "apple", "cucumber", "tomato"};
  }
}
