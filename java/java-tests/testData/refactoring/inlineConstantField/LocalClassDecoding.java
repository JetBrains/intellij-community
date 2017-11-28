
class Foobar {
    void m(Foobar _local) {
        class Local {
            protected Foobar _field = _local;
            {
                System.out.println(this.<caret>_field);
            }
        };
    }
}
