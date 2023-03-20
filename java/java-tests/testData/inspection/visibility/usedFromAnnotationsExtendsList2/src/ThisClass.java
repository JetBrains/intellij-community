import java.util.ArrayList;

@SuppressWarnings(ThisClass.PUBLICFINALNAME)
@Anno(ThisClass.FFF.class)
class ThisClass extends ArrayList<ArrayList<ThisClass.FF>> {
  static final String PUBLICFINALNAME = "stuff";
  static class FF {}
  static class FFF {}

  public static void main(String[] args) {
  }
}
@interface Anno {
  Class<?> value() default String.class;
}