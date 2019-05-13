import java.util.AbstractCollection;
import java.util.EnumSet;
import java.util.function.BiConsumer;

final class Test {

  public static <TEnum extends Enum<TEnum>> void toEnumSet() {
    BiConsumer<EnumSet<TEnum>, TEnum> add = EnumSet::add;
    System.out.println(add);
    BiConsumer<EnumSet<TEnum>, TEnum> add1 = AbstractCollection::add;
    System.out.println(add1);
  }

}