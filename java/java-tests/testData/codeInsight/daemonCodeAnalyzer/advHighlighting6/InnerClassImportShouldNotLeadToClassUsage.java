package p;

import static p.Outer.Inner;
import java.util.*;

abstract class <warning descr="Class 'Outer' is never used">Outer</warning> implements List<Inner> {
  public static class Inner {} 
}