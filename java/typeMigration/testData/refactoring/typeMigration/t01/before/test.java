class Foo {
    Moo moo(int i) {
        return null;
    }
}

class Moo {
    Foo foo(Integer[] j) {
        return null;
    }
}

class P {
    int f(int y) {
        return y;
    }
}

class G extends P {
    int f(int y) {
        return y;
    }
}

public class Test {
    Moo g(Moo i) {
        Foo j = i.foo(new Integer[0]);
        return null;
    }
}
