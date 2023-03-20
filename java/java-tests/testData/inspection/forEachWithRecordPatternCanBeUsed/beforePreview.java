import java.util.List;

public class Preview {

  record StringInteger(String text, Integer number) {
  }

  public static void simple(List<StringInteger> list) {
    for (StringInteger stringInteger<caret> : list) {
      System.out.println(stringInteger.number);
      System.out.println(stringInteger.number());
      System.out.println(stringInteger.text);
      System.out.println(stringInteger.text());
      Integer number = stringInteger.number;
      System.out.println(number);
      System.out.println(number.intValue());
      String text = stringInteger.text();
      System.out.println(text);
      String text1 = stringInteger.text;
    }
  }
}
