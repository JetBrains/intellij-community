class T1 {
    int method(int i) {
        return 0;
    }
}

class T2 extends T1 {
    int method(int i, int anObject) {
        return anObject;
    }
}

class Usage {
    int m() {
        final T2 t2 = new T2();
        return t2.method(0, t2.method(0) + 1);
    }
}