import pkg.PathUtil;

public class SomeClass {
    private void b() {
        a(i -> PathUtil.toSystemDependentName()<caret> s + "Hello");
    }
}

