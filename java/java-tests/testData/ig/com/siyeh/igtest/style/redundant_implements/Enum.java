import java.io.Serializable;

enum ABC implements <warning descr="Redundant interface declaration 'Comparable<ABC>'">Comparable<ABC></warning>, <warning descr="Redundant interface declaration 'Serializable'">Serializable</warning>  {
  a, b, c;
}