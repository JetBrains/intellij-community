// "Create Method 'get'" "true"
class W<T> {
}

class C {
    void foo () {
        W<String> w = new W<String>();
        String s = w.<caret>get("");
    }
}