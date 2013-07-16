import java.util.Collection;

public class IncorrectError extends NarrowClass {
    public Collection<String> bar() {
        return super.doStuff();
    }
}

interface Interface {
    Collection<String> doStuff();
}

class NarrowClass extends BaseClass implements Interface {
}

class BaseClass {
    public Collection doStuff() {
        return null;
    }
}
