import java.util.*;

class BarGoo {}

class Foo {
    {
        List<BarGoo> goos;
        if (true) <caret>
    }
}