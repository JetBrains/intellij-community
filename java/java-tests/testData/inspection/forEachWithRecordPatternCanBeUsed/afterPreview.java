import java.util.List;

public class Preview {

  record StringInteger(String text, Integer number) {
  }

  public static void simple(List<StringInteger> list) {
      for (StringInteger(String text, Integer number) : list) {
          System.out.println(number);
          System.out.println(number);
          System.out.println(text);
          System.out.println(text);
          System.out.println(number);
          System.out.println(number.intValue());
          System.out.println(text);
      }
  }
}
