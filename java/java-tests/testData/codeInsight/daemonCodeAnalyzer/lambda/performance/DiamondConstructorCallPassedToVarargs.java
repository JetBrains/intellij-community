import java.util.*;
import java.util.stream.*;

interface Graph<T> extends Iterable<T> {
  double distance(T from, T to);
  T startVertex();
  default Stream<T> stream() {
    return StreamSupport.stream(spliterator(), false);
  }
}

class GeometricGraph<T extends Comparable<T>> implements Graph<GeometricGraph.Point<T>> {
  private Point<T>[] vertices;
  @SafeVarargs
  public GeometricGraph(Point<T>... vertices) {
    this.vertices = vertices;
  }
  @Override public double distance(Point<T> from, Point<T> to) {
    double dx = to.x - from.x;
    double dy = to.y - from.y;
    return Math.sqrt(dx * dx + dy * dy);
  }
  @Override public Point<T> startVertex() {
    return vertices[0];
  }
  @Override public Iterator<Point<T>> iterator() {
    return Arrays.asList(vertices).iterator();
  }
  public static class Point<T extends Comparable<T>> implements Comparable<Point<T>> {
    final T label;
    final double x;
    final double y;
    public Point(T label, double x, double y) {
      this.label = label;
      this.x = x;
      this.y = y;
    }
    @Override public String toString() {
      return String.valueOf(label);
    }
    @Override public int compareTo(Point<T> o) {
      return label.compareTo(o.label);
    }
  }
}

interface Crash<T> {
  double INF = Double.POSITIVE_INFINITY;
  void run(Graph<T> graph);

  // From http://lcm.csa.iisc.ernet.in/dsa/node186.html, solution a->c->d->e->f->b->a 48.39
  GeometricGraph<Character> CARTESIAN = new GeometricGraph<>(
    new GeometricGraph.Point<>('a', 0, 0),
    new GeometricGraph.Point<>('b', 4, 3),
    new GeometricGraph.Point<>('c', 1, 7),
    new GeometricGraph.Point<>('d', 15, 7),
    new GeometricGraph.Point<>('e', 15, 4),
    new GeometricGraph.Point<>('f', 18, 0)
  );

  // http://www.geomidpoint.com/random/ whole world 100 points
  GeometricGraph<Integer> GEO = new GeometricGraph<>(
    new GeometricGraph.Point<>(1, 41.75887603, 45.54442576),
    new GeometricGraph.Point<>(2, 25.95582633, 53.31372621),
    new GeometricGraph.Point<>(3, 27.10968149, -148.3088281),
    new GeometricGraph.Point<>(4, 47.89312627, 63.62800849),
    new GeometricGraph.Point<>(5, -39.63985521, 22.72245952),
    new GeometricGraph.Point<>(6, 10.75270177, 172.5620158),
    new GeometricGraph.Point<>(7, -5.22786075, 174.0175703),
    new GeometricGraph.Point<>(8, 6.09021552, -174.842083),
    new GeometricGraph.Point<>(9, -41.93929433, -151.4679823),
    new GeometricGraph.Point<>(10, 23.76929542, 52.02191021),
    new GeometricGraph.Point<>(11, -27.07564288, -65.97458804),
    new GeometricGraph.Point<>(12, -44.69115169, 3.9545051),
    new GeometricGraph.Point<>(13, -5.43915001, -67.03701528),
    new GeometricGraph.Point<>(14, -46.80168575, 167.7479893),
    new GeometricGraph.Point<>(15, 3.37026877, -112.5740888),
    new GeometricGraph.Point<>(16, 72.28180933, -29.27517743),
    new GeometricGraph.Point<>(17, -42.08042944, -45.20059984),
    new GeometricGraph.Point<>(18, -5.94878325, 65.81227912),
    new GeometricGraph.Point<>(19, 0.82655482, -137.5756048),
    new GeometricGraph.Point<>(20, -1.89649258, -34.85895025),
    new GeometricGraph.Point<>(21, -8.41830692, 72.91705955),
    new GeometricGraph.Point<>(22, -67.12475398, -30.98024614),
    new GeometricGraph.Point<>(23, 3.27537627, -101.5926056),
    new GeometricGraph.Point<>(24, 28.12320278, 171.0993409),
    new GeometricGraph.Point<>(25, 43.81836686, 153.6367713),
    new GeometricGraph.Point<>(26, -30.26453996, 125.4817181),
    new GeometricGraph.Point<>(27, 30.42399561, 140.6854059),
    new GeometricGraph.Point<>(28, 51.15497569, -118.603574),
    new GeometricGraph.Point<>(29, -26.11317488, 165.3413163),
    new GeometricGraph.Point<>(30, 17.3884151, 109.0310505),
    new GeometricGraph.Point<>(31, -53.28586665, 113.3310133),
    new GeometricGraph.Point<>(32, -36.91984178, 17.53340885),
    new GeometricGraph.Point<>(33, -49.45998685, 111.9311892),
    new GeometricGraph.Point<>(34, -63.1554812, 79.70629564),
    new GeometricGraph.Point<>(35, 28.82084009, -9.14338737),
    new GeometricGraph.Point<>(36, 37.52058234, -0.32285569),
    new GeometricGraph.Point<>(37, 23.58437569, -138.7499972),
    new GeometricGraph.Point<>(38, -28.07522086, -175.3760246),
    new GeometricGraph.Point<>(39, -63.57013678, -100.3303656),
    new GeometricGraph.Point<>(40, 16.2360492, -7.04890614),
    new GeometricGraph.Point<>(41, 32.50586034, -93.26947618),
    new GeometricGraph.Point<>(42, 0.37760791, 114.3663184),
    new GeometricGraph.Point<>(43, -54.95460861, 173.9221499),
    new GeometricGraph.Point<>(44, -62.88777314, 11.02357861),
    new GeometricGraph.Point<>(45, -0.39552891, -10.24023055),
    new GeometricGraph.Point<>(46, -32.82228853, 2.49278472),
    new GeometricGraph.Point<>(47, -21.93177958, 104.425205),
    new GeometricGraph.Point<>(48, 40.66726414, 1.38813168),
    new GeometricGraph.Point<>(49, -10.17461981, -147.9987545),
    new GeometricGraph.Point<>(50, -14.10034262, 115.3193397),
    new GeometricGraph.Point<>(51, -60.18635059, -77.7990411)
  );
}