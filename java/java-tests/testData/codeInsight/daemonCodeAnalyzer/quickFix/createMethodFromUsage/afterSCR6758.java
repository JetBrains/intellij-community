// "Create method 'someMethod'" "true"
public class Test {

    public Object get() {
        return new Object() {
            public boolean equals(Object obj) {
                return someMethod(this);
            }

            private boolean someMethod(Object o) {
                <caret><selection>return false;</selection>
            }
        };
    }
}
