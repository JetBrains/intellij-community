@interface Anno {
  String value();
}

public class FooBar {
    @Anno(FooBar<caret>)
    void m() {}

    String FooBarrrrrr() {}

    public static String FooBarrrr() {}


}
