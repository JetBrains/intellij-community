class A {

    Integer f(int i) {
        return g(i);
    }

    Integer <caret>g(int i) {
        if (i > 0) {
            return new Integer(i);
        }
        else {
            Integer result;
            result = new Integer(0);
            return result;
        }
    }
}