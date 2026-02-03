import java.util.*;

class BarGoo {}

class Foo {
    {
        List<BarGoo> goos;
        if (true) {
            for (BarGoo goo : <selection>goos</selection><caret>) {

            }
        }
    }
}