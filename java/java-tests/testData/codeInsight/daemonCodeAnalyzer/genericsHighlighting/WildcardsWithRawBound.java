class Test {
  <T extends Enum<T>> boolean checkEnum(Class<T> enumClass) {
    Class<? extends Enum> my = null;
    return my== enumClass;
  }
}