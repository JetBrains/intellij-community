// "Create method 'someMethod'" "true-preview"
public class Test {

    public Object get() {
        return new Object() {
            public boolean equals(Object obj) {
                return <caret>someMethod(this);
            }
        };
    }
}
