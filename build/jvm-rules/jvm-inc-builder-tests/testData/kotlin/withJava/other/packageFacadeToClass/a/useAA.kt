fun useAA() {
  // this usage is expected to be (unnecessarily) recompiled, because compiler reports lookup usage "f" in default package "" scope
  AA.f()
}