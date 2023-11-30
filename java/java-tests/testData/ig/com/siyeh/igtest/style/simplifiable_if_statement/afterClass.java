// "Replace 'if else' with '?:'" "true"
class ClassTest {
  Class<?> test(boolean b) {
      return b ? Boolean.class : String.class;
  }
}