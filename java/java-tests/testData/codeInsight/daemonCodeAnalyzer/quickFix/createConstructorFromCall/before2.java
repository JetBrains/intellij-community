// "Create constructor" "true-preview"
public class Test {
    public void main2() {
        new <caret>MyCollection(this);
    }
}

class MyCollection {
}