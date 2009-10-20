@interface Anno {
  String value();
}

@Anno(FooBar.FEE_BAR<caret>)
public class FooBar {
    public static final String FEE_BAR;

    String FooBarrrrrr() {}

    public static String FooBarrrr() {}


}
