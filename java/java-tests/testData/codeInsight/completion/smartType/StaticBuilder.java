public class S {

  {
    Map m = bui<caret>
  }

}

class Map {
  static Builder build() {}

  static class Builder {
    Map get() {}
  }
}