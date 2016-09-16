package foo;

import bar.*;

import static foo.B.*;
import static foo.B.C;

public class A
{
    public static void main()
    {
        new D();
        new D1();
        new D2();
        new D3();
        new D4();
        new C();
        new C1();
        new C2();
        new C3();
    }
}