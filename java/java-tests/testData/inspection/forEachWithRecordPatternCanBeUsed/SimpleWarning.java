import java.util.List;

public class SimpleWarning {

  record StringInteger(String text, Integer number) {
  }

  public static void simple(List<StringInteger> list) {
      for (StringInteger <warning descr="Can be replaced with enhanced 'for' with a record pattern">stringInteger</warning> : list) {
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
