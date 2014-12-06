// "Create enum constant 'D3'" "true"
public enum Demo {

    D1("foo"),
    D2("bar"), D3()<caret>;
    private String name;

    Demo(String name) {

        this.name = name;
    }
}

class Show {

    public void doSomething() {
        Demo.D3
    }
}