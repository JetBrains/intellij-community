// "Create Method 'someMethod'" "true"
public class Test {

    public Object get() {
        return new Object() {
            public boolean equals(Object obj) {
                return <caret>someMethod(this);
            }
        };
    }
}
