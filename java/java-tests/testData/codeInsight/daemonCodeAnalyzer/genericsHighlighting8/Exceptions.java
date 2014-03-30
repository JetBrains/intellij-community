class C <T extends Exception> {
    void foo () throws T {}
    void bar () {
        <error descr="Unhandled exception: T">foo ();</error>
    }

    <T extends Error> void goo() {
        try {
            int i = 12;
        } catch (<error descr="Cannot catch type parameters">T</error> ex) {
        }
    }
}

//IDEADEV-4169: no problem here
interface Blub {
    public <E extends Throwable> void Switch() throws E;
}

class Blib implements Blub {
    public <E extends Throwable> void Switch() throws E {
    }
}
