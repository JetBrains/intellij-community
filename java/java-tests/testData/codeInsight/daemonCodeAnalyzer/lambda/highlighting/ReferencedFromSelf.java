class Test {

  Runnable r;
  { r = r::run; }
  Runnable r1;
  { r1 = () -> r1.run(); }


  {
    Runnable r = () -> <error descr="Variable 'r' might not have been initialized">r</error>.run();
    Runnable r1 = <error descr="Variable 'r1' might not have been initialized">r1</error>::run;
  }
}
