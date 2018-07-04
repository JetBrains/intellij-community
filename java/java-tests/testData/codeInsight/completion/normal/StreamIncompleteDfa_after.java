import java.util.List;
import java.util.Set;

class Boo {
}

class Foo extends Boo {
    String getName() {  return "_name_"; }
}

class Test {
    Set<String> getAllNames(List<Boo> items) {
        return items.stream().filter(x -> x instanceof Foo).map(x -> ((Foo) x).getName())
    }
}