public class S {

  {
    Map m = Map.build()<caret>.get();
  }

}

class Map {
  static Builder build() {}

  static class Builder {
    Map get() {}
  }
}