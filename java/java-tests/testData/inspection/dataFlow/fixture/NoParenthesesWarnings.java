class Fun {
  void smthComplex() {
    Object n  = new Integer(1);
    String result = ((<warning descr="Condition 'n != null' is always 'true'">n != null</warning>) ? n.toString() : "");
  }

}