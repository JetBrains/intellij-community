@interface Anno {
  String value();
}

public class FooBar {
    @Anno(FoB<caret>)
    void m() {}

    String FooBarrrrrr() {}

    public static String FooBarrrr() {}


}
