public class FooBar {

    String getFoo();
    String getBar();

    {
        String s = new FooBar().<caret>
    }

}