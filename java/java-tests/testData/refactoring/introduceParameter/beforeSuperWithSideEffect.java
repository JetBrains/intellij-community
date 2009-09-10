class T1 {
    int method(int i) {
        return 0;
    }
}

class T2 extends T1 {
    int method(int i) {
        return <selection>super.method(i) + 1</selection>;
    }
}

class Usage {
    int m() {
        return new T2().method(0);
    }
}