import java.util.List;

public class Simple {

  record StringInteger(String text, Integer number) {
  }

  public static void simple(List<StringInteger> list) {
      for (StringInteger stringInteger : list) {
        System.out.println(stringInteger.<warning descr="Can be replaced with enhanced 'for' with a record pattern">number</warning>);
        System.out.println(stringInteger.<warning descr="Can be replaced with enhanced 'for' with a record pattern">number</warning>());
        System.out.println(stringInteger.<warning descr="Can be replaced with enhanced 'for' with a record pattern">text</warning>);
        System.out.println(stringInteger.<warning descr="Can be replaced with enhanced 'for' with a record pattern">text</warning>());
        Integer <warning descr="Can be replaced with enhanced 'for' with a record pattern">number</warning> = stringInteger.number;
        System.out.println(number);
        System.out.println(number.intValue());
        String <warning descr="Can be replaced with enhanced 'for' with a record pattern">text</warning> = stringInteger.text();
        System.out.println(text);
        String <warning descr="Can be replaced with enhanced 'for' with a record pattern">text1</warning> = stringInteger.text;
      }
  }
}
