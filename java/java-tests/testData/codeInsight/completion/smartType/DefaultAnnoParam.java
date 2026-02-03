@interface Anno {
  String value();
}

public class FooBar {
    public static final String FOO_BAR;

    @Anno(FB<caret>)
    void m() {}

    String FooBarrrrrr() {}

    public static String FooBarrrr() {}


}
