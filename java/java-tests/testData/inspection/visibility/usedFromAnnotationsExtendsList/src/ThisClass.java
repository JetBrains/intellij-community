import java.util.ArrayList;
@SuppressWarnings(ThisClass.PUBLICFINALNAME)
@Anno(ThisClass.FFF.class)
public class ThisClass extends ArrayList<ThisClass.FF> {
    public static final String PUBLICFINALNAME = "stuff";
    public static class FF {}
    static class FFF {}

    public static void main(String[] args) {
    }
}
@interface Anno {
  Class<?> value() default String.class; 
}