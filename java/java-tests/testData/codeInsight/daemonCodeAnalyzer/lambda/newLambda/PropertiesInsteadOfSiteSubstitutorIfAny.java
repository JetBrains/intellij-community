
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

class Test {
  public static class Thing {
    public Integer toPresenter() {
      return 1;
    }
  }

  public <U> void map1(Function<Object, U> mapper) {}

  private  void getPresentersFailure( final Stream<Thing> stream) {
    map1((roles) -> {
      return stream.map(Thing::toPresenter);
    });
  }

}

class Test1 {
  public interface Convertable {
    ThingPresenter toPresenter();
  }

  public static class Thing implements Convertable {
    @Override
    public ThingPresenter toPresenter() {
      return new ThingPresenter("thing");
    }
  }

  public static class ThingPresenter {
    public String value;
    public ThingPresenter(String value) { this.value = value; }
  }

  private static Stream<ThingPresenter> getPresentersFailure(Supplier<Optional<List<Thing>>> thingSupplier) {
    Optional<List<Thing>> personRoles = thingSupplier.get();
    return personRoles.map(roles -> roles.stream().map(Thing::toPresenter)).get();
  }

  private static Stream<ThingPresenter> getPresentersWorking(Supplier<Optional<List<Thing>>> thingSupplier) {
    Optional<List<Thing>> personRoles = thingSupplier.get();
    return personRoles.map(roles -> roles.stream().map(t -> t.toPresenter())).get();
  }
}