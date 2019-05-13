public class S {

  {
    Map m = bui<caret>
  }

}

class Map {
  static MapBuilder build() {}

  interface BaseBuilder<T> {
    T get();
  }

  interface MapBuilder extends BaseBuilder<Map> {

  }

  static class Builder implements MapBuilder {
    Map get() {}
  }
}