abstract class Path implements Iterable<Path>{}
class Paths {
  public static Path get(String s) {return null;}
}
class Usage {
  public static void main(String[] args) {
      Path path = Paths.get("");
  }
}