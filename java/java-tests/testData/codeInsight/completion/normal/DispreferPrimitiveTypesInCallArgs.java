class Xxx extends Super {
    void mm() {
        bar(d<caret>)
    }

    void bar(Doo d) {}

    static class Doo {
        static Doo doo() {}
    }

}

class Super {
    Object dx() {}
}
