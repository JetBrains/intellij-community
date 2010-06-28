// "Create Method 'someMethod'" "true"
public class Test {

    public Object get() {
        return new Object() {
            public boolean equals(Object obj) {
                return someMethod(this);
            }

            private boolean someMethod(Object o) {
                <selection>return false;  //To change body of created methods use File | Settings | File Templates.<caret></selection>
            }
        };
    }
}
