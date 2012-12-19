import java.util.List;

interface ExampleInterface {
    public List exampleMethod();
}

class ExampleSuperClass {
    public List<String> exampleMethod() {
        return null;
    }
}


public class  ExampleSubClass extends ExampleSuperClass implements ExampleInterface {
}
