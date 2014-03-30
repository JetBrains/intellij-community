import java.util.List;

interface ExampleInterface {
    public List exampleMethod();
}

class ExampleSuperClass {
    public List<String> exampleMethod() {
        return null;
    }
}


class  ExampleSubClass extends ExampleSuperClass implements ExampleInterface {
}
