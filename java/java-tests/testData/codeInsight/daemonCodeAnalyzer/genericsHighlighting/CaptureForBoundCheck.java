
class EnumBug {
  static class Enum<E extends Enum<E>> {
  }

  static class Option<T> extends Enum<Option<T>> {
  }

  static class EnumSet<E extends Enum<E>> {
    static <E extends Enum<E>> EnumSet<E> noneOf(Class<E> elementType) {
      return null;
    }
  }

  public static void main(String[] args) {
    EnumSet<<error descr="Type parameter 'EnumBug.Option' is not within its bound; should extend 'EnumBug.Enum<EnumBug.Option<?>>'">Option<?></error>> enumSet = EnumSet.<<error descr="Type parameter 'EnumBug.Option' is not within its bound; should extend 'EnumBug.Enum<EnumBug.Option<?>>'">Option<?></error>>noneOf(Option.class);
    EnumSet<<error descr="Type parameter 'EnumBug.Option' is not within its bound; should extend 'EnumBug.Enum<EnumBug.Option>'">Option</error>> enumSetRaw = EnumSet.<Option>noneOf(Option.class);
  }

}