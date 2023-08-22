module dep {
  provides java.lang.Runnable with p.Impl;
  uses java.lang.Iterable;
}