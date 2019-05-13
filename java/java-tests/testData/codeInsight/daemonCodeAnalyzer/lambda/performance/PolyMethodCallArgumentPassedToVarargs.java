class GeometricGraph<T extends Comparable<T>> {

  public static <K extends Comparable<K>> GeometricGraph<K> createGraph(Point<K>... points) {
    return null;
  }
}
class Point<T1 extends Comparable<T1>> implements Comparable<Point<T1>> {
  @Override public int compareTo(Point<T1> o) {
    return 0;
  }

  static <M extends Comparable<M>> Point<M> create(M m) {
    return null;
  }
}

class Graph {

  GeometricGraph<Integer> GEO = GeometricGraph.createGraph(
    Point.create(381),
    Point.create(49),
    Point.create(73),
    Point.create(16),
    Point.create(21),
    Point.create(381),
    Point.create(49),
    Point.create(381),
    Point.create(49),
    Point.create(73),
    Point.create(16),
    Point.create(21),
    Point.create(381),
    Point.create(49),
    Point.create(381),
    Point.create(49),
    Point.create(73),
    Point.create(16),
    Point.create(21),
    Point.create(381),
    Point.create(49),
    Point.create(381),
    Point.create(49),
    Point.create(73),
    Point.create(16),
    Point.create(21),
    Point.create(381),
    Point.create(49),
    Point.create(381),
    Point.create(49),
    Point.create(73),
    Point.create(16),
    Point.create(21),
    Point.create(381),
    Point.create(49),
    Point.create(381),
    Point.create(49),
    Point.create(73),
    Point.create(16),
    Point.create(21),
    Point.create(381),
    Point.create(49),
    Point.create(381),
    Point.create(49),
    Point.create(73),
    Point.create(16),
    Point.create(21),
    Point.create(381),
    Point.create(49),
    Point.create(381)
  );
}