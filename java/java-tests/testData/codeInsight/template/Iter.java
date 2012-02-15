import java.util.*;

class BarGoo {}

class Foo {
    {
        List<BarGoo> goos;
        <caret>
    }
}