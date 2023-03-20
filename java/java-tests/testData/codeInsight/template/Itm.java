import java.util.*;

class BarGoo {}

class Foo {
    {
        Map<String, BarGoo> goos;
        <caret>
    }
}