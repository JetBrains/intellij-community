import java.io.Serializable;

class IntersectionTypeCast {
  void m(Runnable r) {
    Object o = <error descr="Intersection types in casts are not supported at language level '1.7'">(Runnable & Serializable)r</error>;
  }
}