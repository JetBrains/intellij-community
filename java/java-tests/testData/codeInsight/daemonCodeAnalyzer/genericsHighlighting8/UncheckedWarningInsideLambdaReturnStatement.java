import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

class Test {

  private Repository repository = null;

  public Stream<Person> test() {
    return repository.all()
      .flatMap(foo -> {

        class AB {
          List<String> a() {
            return <warning descr="Unchecked assignment: 'java.util.ArrayList' to 'java.util.List<java.lang.String>'">new ArrayList()</warning>;
          }
        }

        AB ab = new AB();
        System.out.println(ab);

        if (foo != null) {
          return repository.update();
        }
        return Stream.empty();
      })
      .map(UpdateResult::getPerson);
  }


  public static class Repository {

    public Stream<Person> all() {
      return Stream.empty();
    }

    public Stream<UpdateResult> update() {
      return Stream.empty();
    }

  }

  public static class Person {
  }

  public static class UpdateResult {

    private final Person person;

    private final Object metadata;

    public UpdateResult(Person person, Object metadata) {
      this.person = person;
      this.metadata = metadata;
    }

    public Person getPerson() {
      return person;
    }

    public Object getMetadata() {
      return metadata;
    }
  }
}