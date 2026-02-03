public class SomeClass {
    public SomeClass() {
    }
    public SomeClass(int i, int j) {
    }
    public SomeClass(int j, int i, int k) {
    }

}

class SomeClassImpl extends SomeClass {
    SomeClassImpl(int i, int j) {
        super(i, j);<caret>
    }

}
