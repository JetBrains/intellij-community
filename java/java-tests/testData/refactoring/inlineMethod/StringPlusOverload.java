
class a {
  public static void main(String[] args)
  {
    int x = 2;
    System.out.println(getS<caret>tring(x + 2));
  }

  private static String getString(int number)
  {
    return "" + number;
  }


}
