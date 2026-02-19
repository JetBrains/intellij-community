class X {
  void polyadic(Object o1, Object o2) {
    boolean b1 = o1 instanceof String s && o2 instanceof String <error descr="Variable 's' is already defined in the scope">s</error>;
    boolean b2 = !(o1 instanceof String s) && !(o2 instanceof String <error descr="Variable 's' is already defined in the scope">s</error>);
    boolean b3 = !(o1 instanceof String s) || !(o2 instanceof String <error descr="Variable 's' is already defined in the scope">s</error>);
    boolean b4 = o1 instanceof String s || o2 instanceof String <error descr="Variable 's' is already defined in the scope">s</error>;
    
    // Dubious cases: spec is not very clear about whether this should be accepted
    boolean b5 = o1 instanceof String s && !(o2 instanceof String <error descr="Variable 's' is already defined in the scope">s</error>);
    boolean b6 = o1 instanceof String s && (!(o2 instanceof String <error descr="Variable 's' is already defined in the scope">s</error>) || s.isEmpty());
    boolean b7 = !(o2 instanceof String s) && o1 instanceof String s;
    boolean b8 = (!(o2 instanceof String s) || s.isEmpty()) && o1 instanceof String s && s.isEmpty();
  }
  
  void ternary(Object o1, Object o2, Object o3) {
    // Currently all these samples are accepted by javac
    boolean b1 = o1 instanceof String s ? o2 instanceof String <error descr="Variable 's' is already defined in the scope">s</error> : o3 instanceof String s1;
    boolean b2 = o1 instanceof String s ? o2 instanceof String s1 : o3 instanceof String <error descr="Variable 's' is already defined in the scope">s</error>;
    boolean b3 = o1 instanceof String s1 ? o2 instanceof String s : o3 instanceof String <error descr="Variable 's' is already defined in the scope">s</error>;
    boolean b4 = !(o1 instanceof String s) ? o2 instanceof String <error descr="Variable 's' is already defined in the scope">s</error> : o3 instanceof String s1;
    boolean b5 = !(o1 instanceof String s) ? o2 instanceof String s1 : o3 instanceof String <error descr="Variable 's' is already defined in the scope">s</error>;
    boolean b6 = !(o1 instanceof String s1) ? o2 instanceof String s : o3 instanceof String <error descr="Variable 's' is already defined in the scope">s</error>;
  }
  
  void ternary2(Object o) {
    boolean b1 = (o instanceof String a) ? false : (o instanceof String <error descr="Variable 'a' is already defined in the scope">a</error>);
    boolean b2 = (o instanceof String a) ? false : ((o instanceof String a) ? false : true);
  }
  
  void ifElse(Object o1, Object o2) {
    if (o1 instanceof String s) {
      if (o2 instanceof String <error descr="Variable 's' is already defined in the scope">s</error>) {
        
      }
    }
  }
}