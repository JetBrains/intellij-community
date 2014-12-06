// "Create enum constant 'D3'" "true"
public enum Demo {

    D1("foo"),
    D2("bar");
    private String name;

    Demo(String name) {

        this.name = name;
    }
}

class Show {

    public void doSomething() {
        Demo.D<caret>3
    }
}