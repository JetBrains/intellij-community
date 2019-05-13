abstract class Issue {
  private void enumUsage(Class<? extends Enum> enumClass) {
    passEnumClass(enumClass);
  }

  abstract <E extends Enum<E>> void passEnumClass(Class<E> var1);

  void a(Enum<?> enumValue) {
    Enum constant = Enum.valueOf(enumValue.getClass(), "name");
  }
}