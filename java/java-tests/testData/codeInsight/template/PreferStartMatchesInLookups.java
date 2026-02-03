import java.util.*;

class BarGoo {}

class Foo {
    {
        Map<BarGoo, StringBuilder> goos;

        <caret>
    }
}